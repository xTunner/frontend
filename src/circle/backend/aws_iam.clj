(ns circle.backend.aws-iam
  "fns for working with AWS Identity & Access Management"
  (:use [circle.aws-credentials :only (aws-credentials)])
  (:import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
           com.amazonaws.services.identitymanagement.model.UploadServerCertificateRequest
           com.amazonaws.services.identitymanagement.model.GetServerCertificateRequest))

(defmacro with-iam-client
  [client & body]
  `(let [~client (AmazonIdentityManagementClient. aws-credentials)]
     ~@body))

(defn upload-certificate
  "Uploads an SSL certificate. cert is the certificate public key, it
  begins with ---BEGIN CERTIFICATE---. priv key begins with --BEGIN
  RSA PRIVATE KEY--- "
  [{:keys [name certificate private-key]}]
  (with-iam-client client
    (let [result (.uploadServerCertificate client (UploadServerCertificateRequest. name certificate private-key))]
      (.getServerCertificateMetadata result))))

(defn get-certificate [name]
  (with-iam-client client
    (-> client (.getServerCertificate (GetServerCertificateRequest. name)) (.getServerCertificate) (bean) (update-in [:serverCertificateMetadata] bean))))