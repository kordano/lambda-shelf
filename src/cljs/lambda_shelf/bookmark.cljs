(ns lambda-shelf.bookmark
  (:require [geschichte.sync :refer [client-peer]]
            [geschichte.stage :as s]
            [geschichte.meta :refer [update]]
            [geschichte.repo :as repo]
            [konserve.store :refer [new-mem-store]]
            [hasch.core :refer [sha-1 hash->str uuid]]
            [cljs.core.async :refer [put! take! chan <! >! alts! timeout close! sub]]
            [om.core :as om :include-macros true]
            [clojure.string :refer [blank?]]
            [clojure.set :as set]
            [sablono.core :as html :refer-macros [html]]
            [lambda-shelf.communicator :refer [post-edn get-edn connect!]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(enable-console-print!)

(def host "phobos:8080" #_"shelf.polyc0l0r.net")

;; define live coding vars for geschichte primitives for now

;; the following repository was created with:
#_(.log js/console (str "NEW-INIT-REPO" (repo/new-repository "repo1@shelf.polyc0l0r.net",
                                           {:version 1, :type "http://shelf.polyc0l0r.net"}
                                           "A bookmark app."
                                           true
                                           {:links {"http://www.masteringemacs.org/articles/2013/12/06/introduction-magit-emacs-mode-git/"
                                                    {:comments #{{:title "first!!!!"
                                                                  :text "great news."
                                                                  :date #inst "2014-04-07T13:30:14.686-00:00"
                                                                  :author "eve"}},
                                                     :date #inst "2014-04-07T13:27:23.438-00:00",
                                                     :author "eve"
                                                     :votes #{"eve"},
                                                     :url "http://www.masteringemacs.org/articles/2013/12/06/introduction-magit-emacs-mode-git/",
                                                     :title "An introduction to Magit, an Emacs mode for Git."}}})))



;; a channel where all (metadata)-updates will come in through
;; and which we will realize in a ui through om
(def pub-ch (chan))

(go
  ;; kv-store, platform/app specific, memory is for testing
  (def store (<! (new-mem-store)))

  ;; create a peer, which implements the synching protocol
  (def peer (client-peer "shelf-client" store))

  ;; connect a stage to it and instruct the peer to connect to a remote peer
  (def stage (-> ;; will be loaded from kv-store
              {:meta {:branches {"master" {:heads #{#uuid "05bb531f-02f1-5521-88ef-7f381875ead3"}}}, :id #uuid "1292990f-fb8e-4198-a736-1442277ad7f6", :description "A bookmark app.", :head "master", :last-update #inst "2014-04-22T09:58:41.856-00:00", :schema {:type "http://github.com/ghubber/geschichte", :version 1}, :causal-order {#uuid "05bb531f-02f1-5521-88ef-7f381875ead3" []}, :public true, :pull-requests {}}, :author "repo1@shelf.polyc0l0r.net", :schema {:version 1, :type "http://shelf.polyc0l0r.net"}, :transactions [], :type :meta-sub, :new-values {#uuid "05bb531f-02f1-5521-88ef-7f381875ead3" {:transactions [[#uuid "23fbe3e4-aa6c-5267-b7f8-47f8e6fc140d" #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d"]], :parents [], :ts #inst "2014-04-22T09:58:41.856-00:00", :author "repo1@shelf.polyc0l0r.net", :schema {:version 1, :type "http://shelf.polyc0l0r.net"}}, #uuid "23fbe3e4-aa6c-5267-b7f8-47f8e6fc140d" {:links {"http://www.masteringemacs.org/articles/2013/12/06/introduction-magit-emacs-mode-git/" {:comments #{{:title "first!!!!", :text "great news.", :date #inst "2014-04-07T13:30:14.686-00:00", :author "eve"}}, :date #inst "2014-04-07T13:27:23.438-00:00", :author "eve", :votes #{"eve"}, :url "http://www.masteringemacs.org/articles/2013/12/06/introduction-magit-emacs-mode-git/", :title "An introduction to Magit, an Emacs mode for Git."}}}, #uuid "123ed64b-1e25-59fc-8c5b-038636ae6c3d" '(fn replace [old params] params)}}
              (s/wire-stage peer)
              <!
              s/sync!
              <!
              (s/connect! (str "ws://" host "/geschichte/ws") #_(str "wss://" host "/geschichte/ws"))
              <!
              atom))

  ;; pump updates, starting with init value (safe start)
  (let [[p out] (:chans @stage)]
    (>! pub-ch @stage) ;; init
    (sub p :meta-pub pub-ch)))


;; geschichte is setup


(defn handle-text-change [e owner {:keys [input-text]} input-type]
  (om/set-state! owner [:input-text input-type] (.. e -target -value)))


(defn missing-url-notification
  "Highlight missing url input field for a short time"
  [app owner]
  (go
    (.add (.-classList (om/get-node owner "new-url-form")) "has-error")
    (set! (.-innerHTML (om/get-node owner "new-url-label")) "URL missing")
    (<! (timeout 1300))
    (.remove (.-classList (om/get-node owner "new-url-form")) "has-error")
    (set! (.-innerHTML (om/get-node owner "new-url-label")) "Website")))


(defn missing-protocol-notification
  "Highlight missing url input field for a short time"
  [app owner]
  (go
    (.add (.-classList (om/get-node owner "new-url-form")) "has-error")
    (set! (.-innerHTML (om/get-node owner "new-url-label")) "Wrong protocol, http:// or https:// required at beginning of URL.")
    (<! (timeout 1800))
    (.remove (.-classList (om/get-node owner "new-url-form")) "has-error")
    (set! (.-innerHTML (om/get-node owner "new-url-label")) "Website")))


(defn add-bookmark
  "Collect input data, send it to server and update dom"
  [app owner]
  (go
    (let [new-url (.-value (om/get-node owner "new-url"))
          new-title (.-value (om/get-node owner "new-title"))
          new-comment (.-value (om/get-node owner "new-comment"))
          current-user (.-innerHTML (. js/document (getElementById "current-user-text")))
          add-btn (om/get-node owner "add-btn")
          package {:topic :add :data {:url new-url :title new-title :comment new-comment}}]
      (set! (.-innerHTML add-btn) "Adding...")
      (set! (.-disabled add-btn) true)
      (println "NEW STAGE"
               (-> (swap! stage
                          (fn [old]
                            (-> old
                                (s/transact {:links ;; parameter data
                                             {new-url
                                              {:url new-url
                                               :comments (if-not (empty? new-comment)
                                                           #{{:title ""
                                                              :text new-comment
                                                              :date (js/Date.)
                                                              :author current-user}}
                                                           #{})
                                               :title new-title
                                               :date (js/Date.)
                                               :votes #{}
                                               :author current-user}}}
                                            '(fn [old new] ;; function to apply the parameters
                                               (update-in old [:links]
                                                          (fn [old new]
                                                            (merge-with
                                                             (fn [old new]
                                                               (-> old
                                                                   (update-in [:comments] set/union (:comments new))
                                                                   (update-in [:votes] set/union (:votes new))
                                                                   (update-in [:date] max (:date new))))
                                                             old new))
                                                          (:links new))))
                                repo/commit)))
                   s/sync!
                   <!))
      (set! (.-innerHTML add-btn) "Add!")
      (set! (.-disabled add-btn) false)
      (om/set-state! owner [:input-text :url] "")
      (om/set-state! owner [:input-text :comment] "")
      (om/set-state! owner [:input-text :title] ""))))


(defn- url->hash [u]
  (-> u sha-1 hash->str (subs 0 8)))


(defn add-bookmark-comment
  "Submit new comment and update dom"
  [{:keys [url] :as bookmark} owner]
  (let [comment-field (om/get-node owner (str "new-comment-" (url->hash url) "-group"))
        current-user (.-innerHTML (. js/document (getElementById "current-user-text")))
        new-comment (.-value (om/get-node owner (str "new-comment-" (url->hash url))))]
    (if (= 0 (.-length (.trim new-comment)))
      (.log js/console "_blank")
      (go
        (.add (.-classList comment-field) "csspinner")
        (.add (.-classList comment-field) "traditional")
        (-> (swap! stage
                   (fn [old]
                     (-> old
                         (s/transact {:links
                                      {url {:url url
                                            :comments (if-not (empty? new-comment)
                                                        #{{:title ""
                                                           :text new-comment
                                                           :date (js/Date.)
                                                           :author current-user}}
                                                        #{})
                                            :date (js/Date.)}}}
                                     '(fn [old new]
                                        (update-in old [:links]
                                                   (fn [old new]
                                                     (merge-with
                                                      (fn [old new]
                                                        (-> old
                                                            (update-in [:comments] set/union (:comments new))
                                                            (update-in [:date] max (:date new))))
                                                      old new))
                                                   (:links new))))
                         repo/commit)))
            s/sync!
            <!)
        (om/set-state! owner [:input-text :modal-comment] "")
        (.remove (.-classList comment-field) "csspinner")
        (.remove (.-classList comment-field) "traditional")))))


(defn fetch-url-title
  "Fetch title element of given site and write it to title input field"
  [app owner url]
  (go
    (let [package {:topic :fetch-title :data {:url url}}
          fetch-btn (om/get-node owner "fetch-btn")
          title-input (om/get-node owner "title-group")
          ws-in (om/get-state owner :ws-in)
          ws-out (om/get-state owner :ws-out)]
      (set! (.-innerHTML fetch-btn) "Fetching...")
      (.add (.-classList title-input) "csspinner")
      (.add (.-classList title-input) "traditional")
      (set! (.-disabled fetch-btn) true)
      (>! ws-in package)
      (let [title (-> ws-out <! :title)]
        (set! (.-innerHTML fetch-btn) "Fetch!")
        (.remove (.-classList title-input) "csspinner")
        (.remove (.-classList title-input) "traditional")
        (set! (.-disabled fetch-btn) false)
        (om/set-state! owner [:input-text :title] title)))))

;; serialize vote events, the others cannot be generated that fast
;; TODO find simple stage-update loop vs. input-loop sync pattern
(def votes-ch (chan 100))

(go-loop [url (<! votes-ch)]
  (-> (swap! stage
             (fn [old]
               (-> old
                   (s/transact {:links {url {:url url
                                             :votes #{(uuid)} ;; HACK until user is here
                                             :date (js/Date.)}}}
                               '(fn [old new]
                                  (update-in old [:links]
                                             (fn [old new]
                                               (merge-with (fn [old new]
                                                             (-> old
                                                                 (update-in [:votes] set/union (:votes new))
                                                                 (update-in [:date] max (:date new))))
                                                           old new))
                                             (:links new))))
                   repo/commit)))
      s/sync!
      <!)
  (recur (<! votes-ch)))

;; --- views ---
(defn bookmark-view
  "Bookmark entry in the data table"
  [{:keys [title url date votes comments author] :as bookmark} owner]
  (let [comment-count (count comments)]
    (reify
      om/IRenderState
      (render-state [this {:keys [incoming input-text ws-in ws-out] :as state}]
        (html
         [:tr
          ;; title and collapsed comments
          [:td
           [:a {:href url :target "_blank"} title]

           [:div.panel-collapse.collapse
            {:id (str "comments-panel-" (url->hash url))}

            [:br]

            [:ul.list-group
             (map #(vec [:li.list-group-item
                         [:em.small#comment-user (str (:author %) " - " (.toLocaleTimeString (:date %)))]
                         [:p (:text %)]
                         ]) comments)]

            [:br]

            [:div.form-group {:ref (str "new-comment-" (url->hash url) "-group")}
             [:textarea.form-control
              {:type "text"
               :ref (str "new-comment-" (url->hash url))
               :rows 3
               :value (:modal-comment input-text)
               :style {:resize "vertical"}
               :on-change #(handle-text-change % owner state :modal-comment)
               :placeholder "What do you think?"}]]

            [:button.btn.btn-primary.btn-xs
             {:type "button"
              :on-click (fn [_] (add-bookmark-comment @bookmark owner))}
             "add comment"]]]

          [:td.bookmark-author [:em.small author]]

          [:td.bookmark-date [:em.small (.toLocaleDateString date)]]

          ;; comment counter and toggle
          [:td
           [:a {:href (str "#comments-panel-" (url->hash url))
                :data-parent "#bookmark-table"
                :data-toggle "collapse"}
            [:span.badge
             {:data-toggle "tooltip"
              :data-placement "left"
              :title "Comments"}
             comment-count]]]

          ;; votes
          [:td
           [:button.btn.btn-default.btn-sm
            {:type "button"
             :data-toggle "tooltip"
             :data-placement "left"
             :title "Votes"
             :on-click
             #(put! votes-ch url)}
            [:span (count votes)]
            " \u03BB"]]])))))


(defn pagination-view
  "Simple paging with selectable pages"
  [app owner {:keys [page page-size] :as state}]
  (let [page-count (/ (count (:bookmarks app)) page-size)]
    [:div.text-center
     [:ul.pagination
      (if (= page 0)
        [:li.disabled [:a {:href "#"} "\u00AB"]]
        [:li [:a {:href "#" :on-click (fn [_] (om/set-state! owner :page (dec page)))} "\u00AB"]])

      (map
       #(if (= % page)
          (vec [:li.active
                [:a {:href "#"} (inc %)
                 [:span.sr-only "(current)"]]])
          (vec [:li
                [:a {:href "#" :on-click (fn [_] (om/set-state! owner :page %))}
                 (inc %)]]))
       (range 0 page-count))

      (if (= page (Math/floor page-count))
        [:li.disabled [:a {:href "#"} "\u00BB"]]
        [:li [:a {:href "#" :on-click #(om/set-state! owner :page (inc page))} "\u00BB"]])]]))


(defn sort-and-join
  "Calculates viewable state."
  [repo-val]
  (->> repo-val
       :links
       vals
       (sort-by :date >)
       (map (fn [e] (update-in e [:comments] #(->> % (sort-by :date >) vec))))
       vec))


;; static function to eval map for repo transaction functions
;; this can be done differently (e.g. per symbols), but it is much
;; more elegant and safe to track the table. schema-specific
(def update-fns {'(fn replace [old params] params)
                 (fn replace [old params] params)
                 '(fn [old new]
                    (update-in old [:links]
                               (fn [old new]
                                 (merge-with (fn [old new]
                                               (-> old
                                                   (update-in [:comments] set/union (:comments new))
                                                   (update-in [:votes] set/union (:votes new))
                                                   (update-in [:date] max (:date new))))
                                             old new))
                               (:links new)))
                 (fn [old new]
                   (update-in old [:links]
                              (fn [old new]
                                (merge-with (fn [old new]
                                              (-> old
                                                  (update-in [:comments] set/union (:comments new))
                                                  (update-in [:votes] set/union (:votes new))
                                                  (update-in [:date] max (:date new))))
                                            old new))
                              (:links new)))
                 '(fn [old new]
                    (update-in old [:links]
                               (fn [old new]
                                 (merge-with (fn [old new]
                                               (-> old
                                                   (update-in [:comments] set/union (:comments new))
                                                   (update-in [:date] max (:date new))))
                                             old new))
                               (:links new)))
                 (fn [old new]
                   (update-in old [:links]
                              (fn [old new]
                                (merge-with (fn [old new]
                                              (-> old
                                                  (update-in [:comments] set/union (:comments new))
                                                  (update-in [:date] max (:date new))))
                                            old new))
                              (:links new)))
                 '(fn [old new]
                    (update-in old [:links]
                               (fn [old new]
                                 (merge-with (fn [old new]
                                               (-> old
                                                   (update-in [:votes] set/union (:votes new))
                                                   (update-in [:date] max (:date new))))
                                             old new))
                               (:links new)))
                 (fn [old new]
                   (update-in old [:links]
                              (fn [old new]
                                (merge-with (fn [old new]
                                              (-> old
                                                  (update-in [:votes] set/union (:votes new))
                                                  (update-in [:date] max (:date new))))
                                            old new))
                              (:links new)))})


(defn bookmarks-view
  "Overall view with data table and input fields"
  [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:incoming (chan)
       :fetch (chan)
       :search (chan)
       :ws-in (chan)
       :ws-out (chan)
       :input-text {:url "" :title "" :comment "" :modal-comment "" :search ""}
       :page 0
       :page-size 16})

    om/IWillMount
    (will-mount [_]
      (let [incoming (om/get-state owner :incoming)
            fetch (om/get-state owner :fetch)
            search (om/get-state owner :search)
            ws-in (om/get-state owner :ws-in)
            ws-out (om/get-state owner :ws-out)]
        (go-loop [{:keys [meta] :as pm} (<! pub-ch)
                  slowdown-ch (timeout 0)]
          (when pm
            (<! slowdown-ch)
            (let [new-stage (swap! stage update-in [:meta] update meta)]
         ;;     (println "UPDATING STAGE")
              (if (repo/merge-necessary? (:meta new-stage))
                (<! (s/sync! (swap! stage repo/merge)))
                (let [nval (-> new-stage
                               (s/realize-value store update-fns)
                               <!
                               sort-and-join)]
        ;;          (println "NEW-VALUE:" nval)
                  (om/transact!
                   app
                   :bookmarks
                   (fn [_] nval))))) ;; don't merge too fast or the network will diverge
            (recur (<! pub-ch) (timeout 300))))
        (go
          (let [connection (<! (connect! (str "ws://" host "/bookmark/ws") #_(str "wss://" host "/bookmark/ws")))] ;; for local testing no secure channel
            (om/set-state! owner :ws-in (:in connection))
            (om/set-state! owner :ws-out (:out connection))))
        (go
          (loop []
            (let [[v c] (alts! [fetch search])] ;; todo split in separate loops
              (condp = c
                search (do
                         (om/transact!
                          app
                          :bookmarks
                          (fn [xs]
                            (vec
                             (sort-by :date >
                                      (remove
                                       #(and (if (nil? (% :title))
                                               true
                                               (nil? (.exec (js/RegExp. v) (.toLowerCase (% :title)))))
                                             (if (nil? (% :url))
                                               true
                                               (nil? (.exec (js/RegExp. v) (.toLowerCase (% :url))))))
                                       xs)))))
                         (om/set-state! owner :page 0))

                fetch (fetch-url-title app owner v))
              (recur))))))


    om/IRenderState
    (render-state [this {:keys [incoming search page page-size input-text fetch ws-in ws-out] :as state}]
      (html
       [:div

        ;; container input
        [:div#input-form {:role "form"}
         ;; url
         [:div.form-group {:ref "new-url-form"}
          [:label.control-label {:for "bookmark-url-input" :ref "new-url-label"} "Website"]
          [:input#bookmark-url-input.form-control
           {:type "url"
            :ref "new-url"
            :value (:url input-text)
            :placeholder "URL"
            :on-change #(handle-text-change % owner state :url)
            :onKeyPress #(when (== (.-keyCode %) 13)
                           (if (not (blank? (:url input-text)))
                             (add-bookmark app owner)
                             (missing-url-notification app owner)))}]]

         ;; title input and fetch button
         [:label {:for "bookmark-title-input"} "Name"]
         [:div.input-group {:ref "title-group"}
          [:input#bookmark-title-input.form-control
           {:type "text"
            :ref "new-title"
            :value (:title input-text)
            :placeholder "Title"
            :on-change #(handle-text-change % owner state :title)
            :onKeyPress #(when (== (.-keyCode %) 13)
                           (if (not (blank? (:url input-text)))
                             (add-bookmark app owner)
                             (missing-url-notification app owner)))}]
          [:span.input-group-btn
           [:button.btn.btn-default {:type "button"
                                     :ref "fetch-btn"
                                     :data-loading-text "Fetching ..."
                                     :on-click #(cond (blank? (:url input-text))
                                                      (missing-url-notification app owner)

                                                      (not (re-seq #"(https:)|(http:)" (:url input-text)))
                                                      (missing-protocol-notification app owner)

                                                      :defaut
                                                      (put! fetch (:url input-text)))}
            "Fetch!"]]]

         [:br]

         ;; Comment textarea
         [:div.form-group
          [:label {:for "bookmark-comment-input"} "Comment"]
          [:textarea#bookmark-comment-input.form-control
           {:type "text"
            :ref "new-comment"
            :value (:comment input-text)
            :rows 3
            :style {:resize "vertical"}
            :on-change #(handle-text-change % owner state :comment)
            :placeholder "Write about it ..."}]]]

        [:button.btn.btn-primary
         {:on-click #(if (not (blank? (:url input-text)))
                       (add-bookmark app owner)
                       (missing-url-notification app owner))
          :type "button"
          :ref "add-btn"}
         "Add!"]

        ;; container header
        [:div.row
         [:h2.page-header "Bookmarks"

          [:div.input-group.col-md-2.pull-right
           [:input.form-control.col-md-2.pull-right
            {:type "search"
             :value (:search input-text)
             :placeholder "Search..."
             :on-change #(handle-text-change % owner state :search)
             :onKeyPress #(when (== (.-keyCode %) 13)
                            (if (not (blank? (:search input-text)))
                              (go
                                (>! search (:search input-text)))
                              (go nil
                                  ;; TODO rerealize stage value
                                  )))}]]]]

        ;; bookmark list
        [:div.table-responsive
         [:table.table.table-striped
          [:tbody#bookmark-table
           (om/build-all bookmark-view (take page-size (drop (* page-size page) (:bookmarks app)))
                         {:init-state {:incoming incoming :ws-in ws-in :ws-out ws-out}})]]]

        (pagination-view app owner state)]))))


#_(om/root
   bookmarks-view
   app-state
   {:target (. js/document (getElementById "main"))})
