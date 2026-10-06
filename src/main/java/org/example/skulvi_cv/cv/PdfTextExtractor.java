package org.example.skulvi_cv.cv;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {

    public String extract(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            return clean(text);
        } catch (IOException e) {
            throw new IllegalStateException("CV illisible : " + e.getMessage(), e);
        }
    }

    static String clean(String text) {
        return text.replace('\u00A0', ' ')
                .replace("\r", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}


