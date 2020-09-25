let kubernetes = ./kubernetes.dhall

let mount = ./mount.dhall

let deployment = ./deployment.dhall

let k8s = ./typesUnion.dhall

let cmVolume =
      λ(name : Text) →
      λ(keyAndPath : Text) →
        kubernetes.ConfigMapVolumeSource::{
        , name = Some name
        , items = Some
          [ { key = keyAndPath, path = keyAndPath, mode = None Natural } ]
        }

let testScriptName = "test.sh"
let testScriptKey = "test-script"

let clientVols =
      λ(keytabVol : kubernetes.Volume.Type) →
        [ keytabVol
        , kubernetes.Volume::{
          , name = testScriptKey
          , configMap = Some (cmVolume testScriptKey testScriptName)
          }
        ]

let clientMounts =
      λ(keytabMount : kubernetes.VolumeMount.Type) →
        [ keytabMount
        , mount testScriptKey "/opt/docker/${testScriptName}" (Some testScriptName)
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
              , metadata = kubernetes.ObjectMeta::{ name = Some testScriptKey }
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
