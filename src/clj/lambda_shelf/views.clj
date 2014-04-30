(ns lambda-shelf.views
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :refer [deftemplate after html append defsnippet]]
            [cemerick.friend :as friend]
            [lambda-shelf.quotes :as quotes]
            [clojure.string :refer [blank?]]
            [clojure.java.io :as io]))



(def static-path (some-> (System/getenv "SHELF_STATIC_PATH") read-string))


(defn user-list [current-user users]
  (list
   [:h4 "The Collective"]
   [:ul.list-group
    (map
     #(vec [:li.list-group-item.user-list-item
            [:p [:a {:data-toggle "modal"
                     :data-target "#user-detail"
                     :title "Add friend"}
                 [:span.glyphicon.glyphicon-user]]
             %]])
     (remove #{current-user} users))]))


(defn navbar
  ([]
     [:div.navbar.navbar-default {:role "navigation"}
      [:div.container.col-md-8.col-md-offset-2
       [:div.navbar-header
        [:a.navbar-brand {:href "/"}
         "The Shelf"]]
       [:div.collapse.navbar-collapse
        [:ul.nav.navbar-nav.navbar-right
         [:li
          [:a {:href "login"
               :data-toggle "tooltip"
               :data-placement "bottom"
               :title "Login"}
           [:span.glyphicon.glyphicon-off]]]]]]])
  ([req]
     (let [current-user (-> req friend/identity :current)]
       [:div.navbar.navbar-default {:role "navigation"}
        [:div.container.col-md-8.col-md-offset-2
         [:div.navbar-header
          [:a.navbar-brand {:href "/"}
           "The Shelf"]]
         [:div.collapse.navbar-collapse
          [:p#current-user-text.navbar-text current-user]
          [:ul.nav.navbar-nav.navbar-right
           [:li
            [:a {:href "/"
                 :data-toggle "tooltip"
                 :data-placement "bottom"
                 :title "Browse bookmarks"}
             [:span.glyphicon.glyphicon-bookmark]]]
           [:li
            [:a {:href "find-user"
                 :data-toggle "tooltip"
                 :data-placement "bottom"
                 :title "Find user"}
             [:span.glyphicon.glyphicon-user]]]
           [:li
            [:a {:href "logout"
                 :data-toggle "tooltip"
                 :data-placement "bottom"
                 :title "Logout"}
             [:span.glyphicon.glyphicon-off]]]]]]])))


(deftemplate impressum
  (io/resource "public/index.html")
  []
  [:body]
  (append
   (html
    (navbar)
    [:div.container
     [:div.jumbotron
      [:h2 "Impressum"]
      [:address
       [:strong "The Lambda Collective"] [:br]
       "Konrad Kühne, Christian Weilbach" [:br]
       "Musterstrasse 123" [:br]
       "98765 Musterstadt" [:br]
       [:a {:href "mailto:info@lambda-collective.net"} "mail dump"]]]])))


(deftemplate page
  (io/resource "public/index.html")
  [req]
  [:head]
  (append
   (html
    [:link {:rel "shortcut icon" :href "/favicon.ico"}]))
  [:body]
  (append
   (html
    ;; fork me on github ribbon
    [:a {:href "https://github.com/kordano/lambda-shelf"}
     [:img {:style "position: absolute; top: 0; right: 0; border: 0; z-index: 15;"
            :src "https://camo.githubusercontent.com/a6677b08c955af8400f44c6298f40e7d19cc5b2d/68747470733a2f2f73332e616d617a6f6e6177732e636f6d2f6769746875622f726962626f6e732f666f726b6d655f72696768745f677261795f3664366436642e706e67"
            :alt "Fork me on GitHub"
            :data-canonical-src "https://s3.amazonaws.com/github/ribbons/forkme_right_gray_6d6d6d.png"}]]
    (navbar req)
    [:div#quotes.container.col-md-8.col-md-offset-2
     [:div.well.well-sm
      (quotes/random-quote)]]
    [:div.row
     [:div#main.container.col-md-8.col-md-offset-2]
     [:div#user-list-container.col-md-1.col-md-offset-1]]
    [:div#site-footer.container
     [:p.text-center [:a {:href "impressum"} "Impressum"]]]
    [:script {:src (if static-path
                     (str static-path "/jquery/jquery-1.11.0.min.js")
                     "//ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js")}]
    [:script {:src (if static-path
                     (str static-path "/react/react-0.9.0.min.js")
                     "//fb.me/react-0.9.0.min.js" ;; facebook breaks https on redirect!!
                     )}]
    [:script {:src (if static-path
                     (str static-path "/bootstrap/bootstrap-3.1.1-dist/js/bootstrap.min.js")
                     "//netdna.bootstrapcdn.com/bootstrap/3.1.1/js/bootstrap.min.js")}]
    [:script {:src "js/main.js"}]
    [:script (browser-connected-repl-js)])))


(deftemplate find-user
  (io/resource "public/index.html")
  [req users]
  [:body]
  (append
   (html
    (navbar req)
    [:div.container
     [:h2.page-header "Find a User"]
     [:div#search-form {:role "form"}
      [:form {:method "GET" :action "find-user"}
       [:div.form-group
        [:input.form-control
         {:type "text"
          :name "query"
          :autocomplete "off"
          :placeholder "Search User ..."}]]]]
     (let [query (-> req :params :query)
           current-user (-> req friend/identity :current)
           user-list (sort (remove #{current-user} (keys users)))
           friends (let [user-data (get users current-user)]
                     (if (contains? user-data :friends)
                       (:friends user-data)
                       #{}))]
       [:div.table-responsive
        [:table.table.table-striped
         [:tbody#user-table
          (map
           #(vec (if (friends %)
                   [:tr [:td %] [:td [:span.glyphicon.glyphicon-ok-sign]]]
                   [:form {:method "POST" :action "add-friend"}
                    [:tr
                     [:td [:input {:name "username" :value % :type "hidden"}] %]

                     [:td [:input.btn.btn-primary.btn-xs {:type "submit" :value "Add friend"}]]]]))
           (if (nil? query)
             (take 10 user-list)
             (remove (fn [username] (blank? (re-find (re-pattern query) username))) user-list)))]]])])))


(deftemplate registration
  (io/resource "public/index.html")
  []
  [:body]
  (append
   (html
    (navbar)
    [:div.container
     [:div.col-md-6.col-md-offset-3.col-sm-8.col-sm-offset-2
      [:div#register-panel.panel.panel-primary
       [:div.panel-heading
        [:h3.panel-title "Registration"]]
       [:div.panel-body
        [:div#input-form {:role "form"}
         [:form {:method "POST" :action "register"}

          [:div.form-group
           [:input.form-control
            {:type "email"
             :name "email"
             :autocomplete "off"
             :placeholder "E-mail"}]]

          [:div.form-group
           [:input.form-control
            {:type "password"
             :name "password"
             :autocomplete "off"
             :placeholder "Password"}]]

          [:br]

          [:input.btn.btn-primary
           {:type "submit"
            :value "Register"}]]]]]]]
    [:div#site-footer.container
     [:p.text-center [:a {:href "impressum"} "Impressum"]]])))


(deftemplate login
  (io/resource "public/index.html")
  []
  [:body]
  (append
   (html
    (navbar)
    [:div.container
     [:div.col-md-6.col-md-offset-3.col-sm-8.col-sm-offset-2
      [:div#login-panel.panel.panel-primary
       [:div.panel-heading
        [:h3.panel-title "Welcome to the Shelf"]]
       [:div.panel-body
        [:div#input-form {:role "form"}
         [:form {:method "POST" :action "login"}
          [:div.form-group
           [:input.form-control
            {:type "text"
             :name "username"
             :placeholder "User"}]]

          [:div.form-group
           [:input.form-control
            {:type "password"
             :name "password"
             :placeholder "Password"}]]

          [:br]

          [:input.btn.btn-primary.btn-block
           {:type "submit"
            :value "Login"}]

          [:br]

          [:small "Not a member yet? Sign up " [:a {:href "registration"} "here"] "!"]]]]]]]
    [:div#site-footer.container
     [:p.text-center [:a {:href "impressum"} "Impressum"]]])))
