package com.musicbox.jukebox;

import java.util.ArrayList;
import java.util.List;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.parsetools.RecordParser;

public class ServerVerticle extends AbstractVerticle {

    private final PlaylistStorage playlistStorage = new PlaylistStorage();
    private final List<HttpServerResponse> streamingRoom = new ArrayList<>();
    private StreamState currentStreamState = StreamState.PAUSED;
    private AsyncFile currentFile = null;
    private long fileReaderOffset = 0;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        initialiseFileStreaming();
        vertx.createHttpServer().requestHandler(req -> {
            String uri = req.uri();
            if (uri.contains("/download/audio")) {
                downloadAudio(req);
            } else if (uri.contains("/download/text")) {
                downloadText(req);
            } else if (uri.contains("/stream/audio")) {
                addUserIntoStreamingRoom(req);
            } else if (uri.contains("/stream/play")) {
                currentStreamState = StreamState.PLAYING;
            } else if (uri.contains("/stream/pause")) {
                currentStreamState = StreamState.PAUSED;
            } else {
                req.response().setStatusCode(404).end();
            }
        }).listen(8080);
        startPromise.complete();
    }

    private void initialiseFileStreaming() {
        vertx.setPeriodic(200, ar -> {
            if (currentStreamState == StreamState.PLAYING) {
                if (currentFile == null) {
                    openNextFile();
                }
                currentFile.read(Buffer.buffer(4096), 0, fileReaderOffset, 4096, res -> {
                    if (res.succeeded()) {
                        fileReaderOffset += res.result().length();
                        Buffer result = res.result();
                        for (HttpServerResponse response : streamingRoom) {
                            if (!response.writeQueueFull()) {
                                response.write(result.copy());
                            }
                        }
                    } else {
                        closeCurrentFile();
                    }
                });
            }
        });
    }

    private void addUserIntoStreamingRoom(HttpServerRequest req) {
        HttpServerResponse response = req.response();
        response.setStatusCode(200)
                .setChunked(true);
        streamingRoom.add(response);
    }

    private void downloadAudio(HttpServerRequest req) {
        String parameter = req.getParam("file");
        if (parameter == null) {
            req.response().end("Please specify \"file\" query parameter.");
            return;
        }
        OpenOptions opts = new OpenOptions().setRead(true);
        vertx.fileSystem().open(parameter, opts, ar -> handleAudioFile(ar, req));
    }

    private void downloadText(HttpServerRequest req) {
        System.out.println("Downloading text");
        String parameter = req.getParam("file");
        if (parameter == null) {
            req.response().end("Please specify \"file\" query parameter.");
            return;
        }
        OpenOptions opts = new OpenOptions().setRead(true);
        vertx.fileSystem().open(parameter, opts, ar -> handleTextFile(ar, req));
    }

    private void handleAudioFile(AsyncResult<AsyncFile> ar, HttpServerRequest req) {
        if (ar.succeeded()) {
            AsyncFile asyncFile = ar.result();
            HttpServerResponse resp = req.response();
            resp.setStatusCode(200).putHeader("Content-Type", "audio/mpeg").setChunked(true);
            asyncFile.handler(res -> {
                resp.write(res);
                if (resp.writeQueueFull()) {
                    asyncFile.pause();
                    resp.drainHandler(v -> asyncFile.resume());
                }
            });
            asyncFile.endHandler(e -> resp.end());
        } else {
            req.response().send("No such file.");
        }
    }

    private void handleTextFile(AsyncResult<AsyncFile> ar, HttpServerRequest req) {
        AsyncFile file = ar.result();
        HttpServerResponse resp = req.response();
        resp.setStatusCode(200).putHeader("Content-Type", "text/plain").setChunked(true);
        file.handler(RecordParser.newDelimited("\n", line -> {
            resp.write(line);
            if (resp.writeQueueFull()) {
                file.pause();
                file.drainHandler(e -> file.resume());
            }
        })).endHandler(e -> resp.end());
    }

    private void openNextFile() {
        playlistStorage.proceedWithNextFile();
        OpenOptions opts = new OpenOptions().setRead(true);
        currentFile = vertx.fileSystem()
                .openBlocking(playlistStorage.getNameOfCurrentStreamingFile(), opts);
        fileReaderOffset = 0;
    }

    private void closeCurrentFile() {
        fileReaderOffset = 0;
        currentFile.close();
        currentFile = null;
    }
}
