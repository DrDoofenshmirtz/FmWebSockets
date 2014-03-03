;; Leiningen project file for the FmWebSockets clojure project.
;;
;; Additional dependencies:
;;   fm-core.jar
;;   fm-resources.jar

(def local-mvn-repo-path (-> (str (System/getProperty "user.home")
                                  "/maven-repository")
                             java.io.File.
                             .toURI
                             str))

(defproject fm/websockets "1.0.0"
  :description "FmWebSockets: Clojure HTML5 WebSockets."
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [commons-codec/commons-codec "1.6"]
                 [com.sun.net.httpserver/http "20070405"]
                 [fm/fm-core "1.0.0"]
                 [fm/fm-resources "1.0.0"]]
  :repositories {"mvn-local" ~local-mvn-repo-path}                 
  :aot [fm.websockets.exceptions
        fm.websockets.rpc.types
        fm.websockets.samples.counter.counter-app
        fm.websockets.samples.memprof.memory-profiling-app
        fm.websockets.samples.fileupload.file-upload-app]       
  :jar-name "fm-websockets.jar"
  :omit-source false
  :jar-exclusions [#"(?:^|/).svn/" 
                   #"(?:^|/).git/" 
                   #"(?:^|/)project.clj"])

