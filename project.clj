;; Leiningen project file for the FmWebSockets clojure project.
;;
;; Additional dependencies (must be located the "lib" folder):
;; - fm-core.jar

(defproject fm/websockets "1.0.0"
  :description "FmWebSockets: Clojure HTML5 WebSockets."
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [commons-codec/commons-codec "1.6"]]
  :aot [fm.websockets.exceptions
        fm.websockets.resources.types
        fm.websockets.samples.fileupload.file-upload-app]
  ; don't sweep the "lib" folder when fetching the deps, because
  ; this would delete the additional deps.  
  :disable-deps-clean true    
  :jar-name "fm-websockets.jar"  
  :omit-source false
  :jar-exclusions [#"(?:^|/).svn/" 
                   #"(?:^|/).git/" 
                   #"(?:^|/)project.clj"])

