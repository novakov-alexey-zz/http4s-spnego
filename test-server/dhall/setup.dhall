let kubernetes = ./kubernetes.dhall

let k8s =
      https://raw.githubusercontent.com/dhall-lang/dhall-kubernetes/master/typesUnion.dhall sha256:d7b8c9c574f3c894fa2bca9d9c2bec1fea972bb3acdde90e473bc2d6ee51b5b1

let buildServer = ./server.dhall

let buildClient = ./client.dhall

let deployment = ./deployment.dhall

let mount = ./mount.dhall

let realm = "EXAMPLE.ORG"

let keytabVol =
      kubernetes.Volume::{
      , name = "keytab"
      , secret = Some kubernetes.SecretVolumeSource::{
        , secretName = Some "test-keytab"
        }
      }

let keytabMount = mount "keytab" "/krb5/" (None Text)

let deploymentName = "testserver"

let server = buildServer deploymentName keytabVol keytabMount realm

let client = buildClient deploymentName realm keytabMount keytabVol

in  [ server, client ]
