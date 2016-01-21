(ns bugsnag-tools.core
  "Tools for analyzing bugsnag error data.

   This is an incomplete implementation of the Bugsnag API.  For more details on
   querying the API, see the Bugsnag docs: https://bugsnag.com/docs/api"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]))

(def ^{:private true} host "https://api.bugsnag.com")

(defn- hostify
  "Convenience function for prepending paths with the hostname"
  [path]
  (if (= (subs path 0 4) "http")
    path
    (str host path)))

(defn- fetch
  "Makes an authenticated get request against the bugsnag api."
  [auth-token next-page]
  (when next-page
    (let [response (http/get (hostify next-page) {:query-params {"auth_token" auth-token "per_page" 100}})]
      (lazy-cat (json/decode (:body response) true)
                (fetch auth-token (get-in response [:links :next :href]))))))

(defn accounts
  "Lists the accounts to which the auth-token has access.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"

   Usage:
   => (accounts auth-token error-id)"
  [auth-token]
  (fetch auth-token "/accounts"))

(defn projects
  "Lists the projects on the given account.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"
   account-id \"1a644306e9625e0ad0fd25fa\"

   Usage:
   => (accounts auth-token account-id)"
  [auth-token account-id]
  (fetch auth-token (format "/accounts/%s/projects" account-id)))

(defn errors
  "Returns a lazy sequence of all errors in the given project.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"
   project-id \"1a644306e9625e0ad0fd25fa\"

   Usage:
   => (errors auth-token project-id)
   => (->> (errors auth-token project-id) (take 10))"
  [auth-token project-id]
  (fetch auth-token (format "/projects/%s/errors" project-id)))

(defn events
  "Returns a lazy sequence of all events for the given error.
   Beware, this makes a series of http requests against the API, so it may not
   be very performant for high numbers of events.

   Example parameters:
   auth-token \"b5890f45e45243b70fbffa37ea464633\"
   error-id \"1a644306e9625e0ad0fd25fa\"

   Usage:
   => (events auth-token error-id)
   => (->> (events auth-token error-id) (take 100))"
  [auth-token error-id]
  (fetch auth-token (format "/errors/%s/events" error-id)))
