(ns frontend.components.docs.build-artifacts)

(def article
  {:title "Build artifacts"
   :url :build-artifacts
   :last-updated "August 8, 2013"
   :content [:div
             [:p
              "If your build produces persistent artifacts such as screenshots, coverage reports, ordeployment tarballs, we can automatically save and link them for you."]
             [:p
              "Before each build, we create an empty directory and export its path in theread-only "
              [:code "$CIRCLE_ARTIFACTS"]
              "After the build finishes, everything in this directory is saved and linked to the build."]
             [:p
              [:img.artifacts-image
               {:_ "_",
                :artifacts.png "artifacts.png",
                :docs "docs",
                :img "img",
                :asset-path "asset-path",
                :src " + ($c(assetPath("}]]
             [:p
              "You'll find links to the artifacts at the top of the build page. You can also consume themvia our"]
             [:p "That's all there is to it!"]
             [:p
              "Feel free to + $c(HAML['contact_us']())if you have any questions or feedback!"]]})
