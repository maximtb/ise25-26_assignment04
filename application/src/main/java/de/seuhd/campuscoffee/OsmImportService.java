package de.seuhd.campuscoffee;

import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.data.PosRepository; // falls Pfad anders: anpassen

import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;

@Service
public class OsmImportService {

    private final PosRepository posRepository;

    public OsmImportService(PosRepository posRepository) {
        this.posRepository = posRepository;
    }

    public Pos importFromOsmNode(long nodeId) {
        try {
            String url = "https://www.openstreetmap.org/api/0.6/node/" + nodeId;
            String xml = httpGet(url);

            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new java.io.ByteArrayInputStream(xml.getBytes()));
            doc.getDocumentElement().normalize();

            Element node = (Element) doc.getElementsByTagName("node").item(0);
            if (node == null) throw new IllegalArgumentException("Kein <node>-Element in OSM-Antwort.");

            // Koordinaten
            String latStr = node.getAttribute("lat");
            String lonStr = node.getAttribute("lon");
            if (latStr.isBlank() || lonStr.isBlank()) {
                throw new IllegalArgumentException("Latitude/Longitude fehlen.");
            }

            // Relevante Tags
            NodeList tags = node.getElementsByTagName("tag");
            String name = null, street = null, houseNumber = null, city = null;
            Integer postalCode = null;

            for (int i = 0; i < tags.getLength(); i++) {
                Element tag = (Element) tags.item(i);
                String k = tag.getAttribute("k");
                String v = tag.getAttribute("v");
                switch (k) {
                    case "name":            name = v; break;
                    case "addr:street":     street = v; break;
                    case "addr:housenumber":houseNumber = v; break;
                    case "addr:city":       city = v; break;
                    case "addr:postcode":
                    case "addr:postalcode":
                        try { postalCode = Integer.valueOf(v); } catch (NumberFormatException ignore) {}
                        break;
                }
            }

            if (name == null || street == null || city == null) {
                throw new IllegalArgumentException("Erforderliche Attribute fehlen (name/street/city).");
            }

            LocalDateTime now = LocalDateTime.now();

            Pos pos = Pos.builder()
                    .id(null)
                    .createdAt(now)
                    .updatedAt(now)
                    .name(name)
                    .description("Imported from OpenStreetMap (node " + nodeId + ")")
                    .type(PosType.CAFE)        // ggf. an deinen Enum anpassen
                    .campus(CampusType.CAMPUS) // ggf. an deinen Enum anpassen
                    .street(street)
                    .houseNumber(houseNumber != null ? houseNumber : "")
                    .postalCode(postalCode != null ? postalCode : 0)
                    .city(city)
                    .build();

            return posRepository.save(pos);

        } catch (IllegalArgumentException e) {
            throw e; // für 400 Bad Request im Controller
        } catch (Exception e) {
            throw new RuntimeException("Fehler beim OSM-Import: " + e.getMessage(), e);
        }
    }

    private String httpGet(String url) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("OSM HTTP " + resp.statusCode());
        return resp.body();
    }
}
