let kubernetes = ./kubernetes.dhall

let mount = ./mount.dhall

let deployment = ./deployment.dhall

let k8s =
      https://raw.githubusercontent.com/dhall-lang/dhall-kubernetes/master/typesUnion.dhall sha256:d7b8c9c574f3c894fa2bca9d9c2bec1fea972bb3acdde90e473bc2d6ee51b5b1

let cmVolume =
      λ(name : Text) →
      λ(keyAndPath : Text) →
        kubernetes.ConfigMapVolumeSource::{
        , name = Some name
        , items = Some
          [ { key = keyAndPath, path = keyAndPath, mode = None Natural } ]
        }

let testScriptName = "test.sh"

let clientVols =
      λ(keytabVol : kubernetes.Volume.Type) →
        [ keytabVol
        , kubernetes.Volume::{
          , name = "test-script"
          , configMap = Some (cmVolume "test-script" testScriptName)
          }
        ]

let clientMounts =
      λ(keytabMount : kubernetes.VolumeMount.Type) →
        [ keytabMount
        , mount "test-script" "/opt/docker/test.sh" (Some "test.sh")
        ]

let testScript =
      λ(serverHost : Text) →
      λ(realm : Text) →
        ''
        kinit -kt /krb5/client.keytab test-client@${realm}
        SERVER_HOSTNAME=http://${serverHost}:8080
        curl -v -k --negotiate -u : -b ~/cookiejar.txt -c ~/cookiejar.txt $SERVER_HOSTNAME/auth
        ''

let buildClient =
      λ(deploymentName : Text) →
      λ(realm : Text) →
      λ(keytabMount : kubernetes.VolumeMount.Type) →
      λ(keytabVol : kubernetes.Volume.Type) →
        let clientTestScript =
              kubernetes.ConfigMap::{
              , metadata = kubernetes.ObjectMeta::{ name = Some "test-script" }
              , data = Some
                [ { mapKey = testScriptName
                  , mapValue = testScript deploymentName realm
                  }
                ]
              }

        let clientDeployment =
              deployment
                "client"
                (None (List kubernetes.EnvVar.Type))
                (clientVols keytabVol)
                (clientMounts keytabMount)
                (Some [ "sleep", "1000000" ])

        in  { apiVersion = "v1"
            , kind = "List"
            , items =
              [ k8s.Deployment clientDeployment
              , k8s.ConfigMap clientTestScript
              ]
            }

in  buildClient
