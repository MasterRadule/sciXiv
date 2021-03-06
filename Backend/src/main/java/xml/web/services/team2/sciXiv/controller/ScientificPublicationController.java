package xml.web.services.team2.sciXiv.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xml.web.services.team2.sciXiv.dto.SciPubDTO;
import xml.web.services.team2.sciXiv.dto.SearchPublicationsDTO;
import xml.web.services.team2.sciXiv.dto.StringDTO;
import xml.web.services.team2.sciXiv.exception.ChangeProcessStateException;
import xml.web.services.team2.sciXiv.exception.DocumentLoadingFailedException;
import xml.web.services.team2.sciXiv.exception.DocumentParsingFailedException;
import xml.web.services.team2.sciXiv.exception.InvalidDataException;
import xml.web.services.team2.sciXiv.exception.InvalidXmlException;
import xml.web.services.team2.sciXiv.service.ScientificPublicationService;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;

@RestController
@RequestMapping(value = "/scientificPublication")
@CrossOrigin
public class ScientificPublicationController {

    @Autowired
    private ScientificPublicationService scientificPublicationService;

    @GetMapping
    public ResponseEntity<Object> findByNameAndVersion(@RequestParam String name, @RequestParam int version) {
        try {
            String sciPub = scientificPublicationService.findByNameAndVersion(name, version);
            StringDTO result = new StringDTO(sciPub);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (DocumentLoadingFailedException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while retrieving document", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping("version")
    public ResponseEntity<Object> getLastVersionNumber(@RequestParam String title) {
        try {
            return new ResponseEntity<>(scientificPublicationService
                    .getLastVersionNumber(title.replace(" ", "")), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while retrieving version number", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "xhtml", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Object> getScientificPublicationAsXHTML(@RequestParam String title) {
        try {
            return new ResponseEntity<>(scientificPublicationService
                    .getScientificPublicationAsXHTML(title), HttpStatus.OK);
        } catch (DocumentLoadingFailedException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while retrieving document", HttpStatus.BAD_REQUEST);
        }
    }
    
    @GetMapping(value = "anonymus/xhtml", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Object> getAnonymusScientificPublicationAsXHTML(@RequestParam String title, @RequestParam int version) {
        try {
            return new ResponseEntity<>(scientificPublicationService.getAnonymusScientificPublicationAsXHTML(title, version), HttpStatus.OK);
        } catch (DocumentLoadingFailedException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while retrieving document", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "export/xhtml")
    public ResponseEntity<Object> exportScientificPublicationAsXHTML(@RequestParam String title) {
        try {
            Resource resource = scientificPublicationService.exportScientificPublicationAsXHTML(title);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while exporting publication as HTML", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "export/pdf")
    public ResponseEntity<Object> exportScientificPublicationAsPDF(@RequestParam String title) {
        try {
            Resource resource = scientificPublicationService.exportScientificPublicationAsPDF(title);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while exporting publication as PDF", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "metadata")
    public ResponseEntity<Object> getPublicationsMetadata(@RequestParam String title) {
        return new ResponseEntity<>(scientificPublicationService.getPublicationsMetadata(title), HttpStatus.OK);
    }

    @GetMapping(value = "metadata/rdf")
    public ResponseEntity getPublicationsMetadataAsRDF(@RequestParam String title, HttpServletResponse response) {
        try {
            Resource resource = scientificPublicationService.getPublicationsMetadataAsRDF(title);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while exporting metadata as RDF", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "metadata/json")
    public ResponseEntity<Object> getPublicationsMetadataAsJSON(@RequestParam String title) {
        try {
            Resource resource = scientificPublicationService.getPublicationsMetadataAsJSON(title);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while exporting metadata as JSON", HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping
    public ResponseEntity<Object> addScientificPublication(@RequestBody String sciPub) {
        try {
            scientificPublicationService.save(sciPub);
            return new ResponseEntity<>(201, HttpStatus.CREATED);
        } catch (DocumentParsingFailedException | ChangeProcessStateException | InvalidDataException | InvalidXmlException e) {
        	e.printStackTrace();
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
        	e.printStackTrace();
            return new ResponseEntity<>("An error occurred while saving document", HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping(value = "revise")
    public ResponseEntity<Object> reviseScientificPublication(@RequestBody String sciPub) {
        try {
            scientificPublicationService.revise(sciPub);
            return new ResponseEntity<>(200, HttpStatus.OK);
        } catch (DocumentParsingFailedException | ChangeProcessStateException | InvalidDataException | InvalidXmlException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while revising document", HttpStatus.BAD_REQUEST);
        }
    }

    @PutMapping(value = "withdraw")
    public ResponseEntity<Object> withdrawScientificPublication(@RequestParam String title) {
        try {
            scientificPublicationService.withdraw(title);
            return new ResponseEntity<>(200, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while withdrawing document", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "basicSearch")
    public ResponseEntity<Object> basicSearch(@RequestParam String parameter) {
        try {
            return new ResponseEntity<>(scientificPublicationService.basicSearch(parameter), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while searching documents", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "advancedSearch")
    public ResponseEntity<Object> advancedSearch(SearchPublicationsDTO searchParameters) {
        try {
            return new ResponseEntity<>(scientificPublicationService.advancedSearch(searchParameters), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>("An error occurred while searching documents", HttpStatus.BAD_REQUEST);
        }
    }

    @GetMapping(value = "references")
    public ResponseEntity<ArrayList<SciPubDTO>> getReferences(@RequestParam String title) {
        return new ResponseEntity<>(scientificPublicationService.getReferences(title), HttpStatus.OK);
    }
}
