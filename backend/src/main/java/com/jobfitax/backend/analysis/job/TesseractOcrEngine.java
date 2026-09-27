package com.jobfitax.backend.analysis.job;

import static org.bytedeco.leptonica.global.leptonica.pixDestroy;
import static org.bytedeco.leptonica.global.leptonica.pixRead;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.TessBaseAPI;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TesseractOcrEngine {

    private volatile Path dataDirectory;

    public String recognize(Path imagePath) throws IOException {
        Path tessdata = prepareDataDirectory();
        try (TessBaseAPI api = new TessBaseAPI()) {
            if (api.Init(tessdata.toString(), "kor+eng") != 0) {
                throw new IOException("OCR 엔진을 초기화하지 못했습니다.");
            }

            PIX image = pixRead(imagePath.toString());
            if (image == null) {
                api.End();
                throw new IOException("OCR 이미지 형식을 읽지 못했습니다.");
            }

            try {
                api.SetImage(image);
                try (BytePointer output = api.GetUTF8Text()) {
                    return output == null ? "" : output.getString();
                }
            } finally {
                api.End();
                pixDestroy(image);
            }
        }
    }

    private Path prepareDataDirectory() throws IOException {
        if (dataDirectory != null) {
            return dataDirectory;
        }
        synchronized (this) {
            if (dataDirectory != null) {
                return dataDirectory;
            }
            Path directory = Files.createTempDirectory("jobfit-tessdata-");
            copyResource("kor.traineddata", directory);
            copyResource("eng.traineddata", directory);
            dataDirectory = directory;
            return directory;
        }
    }

    private void copyResource(String filename, Path directory) throws IOException {
        ClassPathResource resource = new ClassPathResource("tessdata/" + filename);
        try (InputStream input = resource.getInputStream()) {
            Files.copy(input, directory.resolve(filename));
        }
    }
}
