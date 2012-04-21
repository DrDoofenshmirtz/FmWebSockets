(def local-repo-path (-> (java.io.File. "/home/frank/local-mvn-repo") .toURI str))

(defproject fm/websockets "1.0.0"
  :description "FmWebSockets: Clojure HTML5 WebSockets."
  :dependencies [[fm.clojure/clojure "1.2.0"]
                 [fm.clojure/clojure-contrib "1.2.0"]
                 [fm/fm-core "1.0.2"]]
  :disable-deps-clean true    
  :repositories {"fm-local" ~local-repo-path}
  :jar-name "fm-websockets.jar"
  :omit-source false
  :jar-exclusions [#"(?:^|/).svn/" 
                   #"(?:^|/).git/" 
                   #"(?:^|/)project.clj"])
