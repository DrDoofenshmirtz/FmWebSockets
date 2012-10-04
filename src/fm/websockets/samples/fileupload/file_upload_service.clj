(ns fm.websockets.samples.fileupload.file-upload-service)

(defn start-upload [connection file-name]
  (println (format "Starting upload of file '%s'..." file-name)))
