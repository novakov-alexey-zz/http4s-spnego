let kubernetes = ./kubernetes.dhall

let buildServer = ./server.dhall

let buildClient = ./client.dhall

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

let serverDeployment = "testserver"

let server = buildServer serverDeployment keytabVol keytabMount realm

let client = buildClient serverDeployment realm keytabMount keytabVol

in  [ server, client ]
