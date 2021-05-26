package com.musicbox.first;

import java.io.File;

import io.netty.handler.codec.http.HttpRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Promise;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.parsetools.RecordParser;

public class ServerVerticle extends AbstractVerticle{
        @Override
        public void start(Promise<Void> startPromise) throws Exception {
            vertx.createHttpServer()
                .requestHandler(req->{
                    String uri = req.uri();
                    if(uri.contains("/stream/audio")){
                        streamAudio(req);
                    }else if (uri.contains("/download/text")){
                        System.out.println("Server got request to text file download");
                        downloadText(req);
                    }else{
                        req.response().setStatusCode(404).end();
                    }
                }).listen(8080);
            startPromise.complete();
        }

        private void streamAudio(HttpServerRequest req){
            String parameter = req.getParam("file");
            if(parameter == null){
                req.response().end("Please specify \"file\" query parameter.");
                return;
            }
            OpenOptions opts = new OpenOptions().setRead(true);
            vertx.fileSystem().open(parameter, opts, ar-> handleAudioFile(ar, req));
        }


        private void downloadText(HttpServerRequest req){
            System.out.println("Downloading text");
            String parameter = req.getParam("file");
            if(parameter == null) {
                req.response().end("Please specify \"file\" query parameter.");
                return;    
            }
            OpenOptions opts = new OpenOptions().setRead(true);
            vertx.fileSystem().open(parameter, opts, ar-> handleTextFile(ar, req));
        }

        private void handleAudioFile(AsyncResult<AsyncFile> ar, HttpServerRequest req){
            if(ar.succeeded()){
                AsyncFile asyncFile = ar.result();
                HttpServerResponse resp = req.response();
                    resp.setStatusCode(200)
                        .putHeader("Content-Type", "audio/mpeg")
                        .setChunked(true);
                asyncFile.handler(res -> {
                    resp.write(res);
                    if(resp.writeQueueFull()){
                        asyncFile.pause();
                        resp.drainHandler(v->asyncFile.resume());
                    }
                });
                asyncFile.endHandler(e->resp.end());
            }else{
                req.response().send("No such file.");
            }
        }

        private void handleTextFile(AsyncResult<AsyncFile> ar, HttpServerRequest req){
            AsyncFile file = ar.result();
            HttpServerResponse resp = req.response();
            resp.setStatusCode(200)
                .putHeader("Content-Type", "text/plain")
                .setChunked(true);
            file.handler(RecordParser.newDelimited("\n", line ->{
                resp.write(line);
                if(resp.writeQueueFull()){
                    file.pause();
                    file.drainHandler(e->file.resume());
                }
            }))
            .endHandler(e->resp.end());
        }
}
