(ns
  ^{:doc 
  
  "A resource finder that maps resource requests to local files."
  
    :author "Frank Mosebach"}
  fm.websockets.file-resource-finder
  (:require
    [clojure.contrib.logging :as log]
    [fm.core.io :as io])
  (:import
    (java.io FileInputStream ByteArrayOutputStream)
    (java.net HttpURLConnection)))

(def ^{:private true :const true} extension->content-type 
                                  {"css"  "text/css"
                                   "js"   "text/javascript"
                                   "jpeg" "image/jpeg"
                                   "gif"  "image/gif"
                                   "png"  "image/png"})

(defn- extension [resource-path]
  (let [path-length (.length resource-path)]
    (when (> path-length 2)
      (let [dot-index (.lastIndexOf resource-path ".")]
        (when (and (pos? dot-index) (< dot-index (dec path-length)))
          (.substring resource-path (inc dot-index)))))))

(defn- response [status content-type content]
  {:status status :content-type content-type :content content})

(defn- file->bytes [path]
  (with-open [input  (FileInputStream. path) 
              output (ByteArrayOutputStream.)]
    (doseq [chunk (io/byte-array-seq input)]
      (.write output chunk))
    (.toByteArray output)))

(defn- find-app [path]
  (log/debug (format "Find app at %s..." path))
  (response HttpURLConnection/HTTP_OK "text/html" (file->bytes path)))

(defn- find-resource [path]
  (log/debug (format "Find resource at %s..." path))
  (if-let [content-type (extension->content-type (extension path))]
    (response HttpURLConnection/HTTP_OK content-type (file->bytes path))))

(defn- join-paths [head tail]
  (cond
    (.endsWith head "/")   
    (recur (.substring head 0 (dec (.length head))) tail)
    
    (.startsWith tail "/") 
    (recur head (.substring tail 1))
    
    :else (str head "/" tail)))

(defn finder [root-path app-path]
  (fn [request]
    (log/debug (format "Process resource request: %s." request))
    (let [{:keys [app-name resource-path]} request]
      (if (= "/" resource-path)
        (find-app (join-paths root-path app-path))
        (find-resource (join-paths root-path resource-path))))))

