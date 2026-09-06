package com.flex.elefin.networking;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Download direct YouTube media in bounded URL ranges, avoiding open-ended HTTP requests. */
public final class TrailerMediaDownloader {
    private static final long CHUNK_SIZE = 1024 * 1024;
    private static final Pattern LENGTH = Pattern.compile("[?&]clen=(\\d+)(?:&|$)");

    private TrailerMediaDownloader() { }

    public static void download(String url, File destination) throws IOException {
        Matcher length = LENGTH.matcher(url);
        if (!length.find()) throw new IOException("Trailer media length is missing");
        long total = Long.parseLong(length.group(1));
        if (total <= 0) throw new IOException("Trailer media is empty");
        boolean complete = false;
        try (FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[32 * 1024];
            for (long offset = 0; offset < total; offset += CHUNK_SIZE) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Download cancelled");
                long end = Math.min(offset + CHUNK_SIZE - 1, total - 1);
                // A range URL represents just this chunk. Its Content-Length/Content-Range
                // describe the chunk, so use the signed clen parameter for the full media size.
                String chunkUrl = url + "&range=" + offset + "-" + end;
                HttpURLConnection connection = (HttpURLConnection) new URL(chunkUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                try {
                    int status = connection.getResponseCode();
                    if (status != 200 && status != 206) {
                        throw new IOException("Trailer media HTTP " + status + " at byte " + offset);
                    }
                    long remaining = end - offset + 1;
                    String sizeHeader = connection.getHeaderField("Content-Length");
                    long responseLength = sizeHeader == null ? -1 : Long.parseLong(sizeHeader);
                    if (responseLength >= 0 && responseLength != remaining) {
                        throw new IOException("Unexpected trailer chunk length");
                    }
                    try (InputStream input = connection.getInputStream()) {
                        while (remaining > 0) {
                            int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (count < 0) throw new IOException("Incomplete trailer media");
                            output.write(buffer, 0, count);
                            remaining -= count;
                        }
                        if (input.read() != -1) throw new IOException("Oversized trailer chunk");
                    }
                } finally {
                    connection.disconnect();
                }
            }
            output.flush();
            complete = true;
        } finally {
            if (!complete) destination.delete();
        }
    }
}
