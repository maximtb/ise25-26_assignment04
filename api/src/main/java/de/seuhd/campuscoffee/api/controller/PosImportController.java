package de.seuhd.campuscoffee.api.controller;

import de.seuhd.campuscoffee.application.OsmImportService;
import de.seuhd.campuscoffee.domain.model.Pos;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pos")
public class PosImportController {

    private final OsmImportService osmImportService;

    public PosImportController(OsmImportService osmImportService) {
        this.osmImportService = osmImportService;
    }

    @PostMapping("/import/{osmNodeId}")
    public ResponseEntity<?> importFromOsm(@PathVariable long osmNodeId) {
        try {
            Pos saved = osmImportService.importFromOsmNode(osmNodeId);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException bad) {
            return ResponseEntity.badRequest().body(bad.getMessage());
        } catch (RuntimeException ex) {
            return ResponseEntity.status(502).body("Upstream/Parsing error: " + ex.getMessage());
        }
    }
}

