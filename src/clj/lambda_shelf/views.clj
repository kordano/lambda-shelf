(ns lambda-shelf.views
  (:require [cemerick.austin.repls :refer (browser-connected-repl-js)]
            [net.cgrand.enlive-html :refer [deftemplate after html append]]
            [cemerick.friend :as friend]
            [lambda-shelf.quotes :as quotes]
            [clojure.java.io :as io]))

(deftemplate impressum
  (io/resource "public/index.html")
  []
  [:body]
  (append
   (html
    [:div.container
     [:div.jumbotron
      [:h2 "Impressum"]
      [:address
       [:strong "The Lambda Collective"] [:br]
       "Konrad Kühne, Christian Weilbach" [:br]
       "Musterstrasse 123" [:br]
       "98765 Musterstadt" [:br]
       [:a {:href "mailto:info@lambda-collective.net"} "mail dump"]]]])))

(def static-path (some-> (System/getenv "SHELF_STATIC_PATH") read-string))

(deftemplate page
  (io/resource "public/index.html")
  [req users]
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
    [:div.navbar.navbar-default {:role "navigation"}
     [:div.container
      [:div.navbar-header
       [:a.navbar-brand {:href "#"}
        "The Shelf"]]
      [:div.collapse.navbar-collapse
       [:p#current-user-text.navbar-text (-> req friend/identity :current)]
       [:ul.nav.navbar-nav.navbar-right
        [:li
         [:a {:href "#"
              :data-toggle "tooltip"
              :data-placement "bottom"
              :title "Import bookmarks"}
          [:span.glyphicon.glyphicon-import]]]
        [:li
         [:a {:href "/bookmark/export.edn"
              :data-toggle "tooltip"
              :data-placement "bottom"
              :title "Export bookmarks"}
          [:span.glyphicon.glyphicon-export]]]
        [:li
         [:a {:href "logout"
              :data-toggle "tooltip"
              :data-placement "bottom"
              :title "Logout"}
          [:span.glyphicon.glyphicon-off]]]]]]]
    [:div#quotes.container
     [:div.well.well-sm
      (quotes/random-quote)]]
    [:div.row
     [:div#main.container.col-md-8.col-md-offset-2]
     [:div.col-md-1.col-md-offset-1
      [:ul.list-group
       (map #(vec [:li.list-group-item.user-list-item
                   [:p %]]) (remove #{(-> req friend/identity :current)}  users))]]]
    [:div#site-footer.container
     [:p.text-center [:a {:href "impressum" :target "_blank"} "Impressum"]]]
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


(deftemplate registration
  (io/resource "public/index.html")
  []
  [:body]
  (append
   (html
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
             :placeholder "e-mail"}]]


          [:div.form-group
           [:input.form-control
            {:type "password"
             :name "password"
             :autocomplete "off"
             :placeholder "Password"}]]

          [:br]

          [:input.btn.btn-primary.btn
           {:type "submit"
            :value "Register"}]]]]]]]
    [:div#site-footer.container
     [:p.text-center [:a {:href "impressum" :target "_blank"} "Impressum"]]]
    )))


(deftemplate login
  (io/resource "public/index.html")
  []
  [:body]
  (append
   (html
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
     [:p.text-center [:a {:href "impressum" :target "_blank"} "Impressum"]]]
    )))
