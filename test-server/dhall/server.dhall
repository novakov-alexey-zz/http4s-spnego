let kubernetes = ./kubernetes.dhall

let k8s =
      https://raw.githubusercontent.com/dhall-lang/dhall-kubernetes/master/typesUnion.dhall sha256:d7b8c9c574f3c894fa2bca9d9c2bec1fea972bb3acdde90e473bc2d6ee51b5b1

let deployment = ./deployment.dhall

let mount = ./mount.dhall

let jaasConfKey = "server-jaas.conf"

let serverEnvVars =
      λ(principal : Text) →
        Some
          [ kubernetes.EnvVar::{
            , name = "JAAS_CONF_PATH"
            , value = Some "/opt/docker/${jaasConfKey}"
            }
          , kubernetes.EnvVar::{ name = "PRINCIPAL", value = Some principal }
          , kubernetes.EnvVar::{
            , name = "KEYTAB"
            , value = Some "/krb5/krb5.keytab"
            }
          ]

let jaasConf =
      λ(principal : Text) →
        ''
        Server  {
            com.sun.security.auth.module.Krb5LoginModule required
            debug=true
            useTicketCache=true
            storeKey=true
            doNotPrompt=true
            renewTGT = true
            useKeyTab=true
            isInitiator=false
            refreshKrb5Config=true
            ticketCache="/tmp/krb"
            keyTab="/krb5/krb5.keytab"
            principal="${principal}";
        };
        ''

let jaasConfMap =
      λ(principal : Text) →
        kubernetes.ConfigMap::{
        , metadata = kubernetes.ObjectMeta::{ name = Some "jaas-conf" }
        , data = Some
          [ { mapKey = jaasConfKey, mapValue = jaasConf principal } ]
        }

let labels = λ(name : Text) → Some (toMap { deployment = name })

let service =
      λ(deploymentName : Text) →
        kubernetes.Service::{
        , metadata = kubernetes.ObjectMeta::{ name = Some deploymentName }
        , spec = Some kubernetes.ServiceSpec::{
          , ports = Some
            [ kubernetes.ServicePort::{
              , name = Some "http"
              , port = 8080
              , protocol = Some "TCP"
              , targetPort = Some (kubernetes.IntOrString.Int 8080)
              }
            ]
          , selector = labels deploymentName
          , type = Some "ClusterIP"
          }
        }

let cmVolume =
      λ(name : Text) →
      λ(keyAndPath : Text) →
        kubernetes.ConfigMapVolumeSource::{
        , name = Some name
        , items = Some
          [ { key = keyAndPath, path = keyAndPath, mode = None Natural } ]
        }

let buildServer =
      λ(deploymentName : Text) →
      λ(keytabVol : kubernetes.Volume.Type) →
      λ(keytabMount : kubernetes.VolumeMount.Type) →
      λ(realm : Text) →
        let principal = "HTTP/${deploymentName}.test.svc.cluster.local@${realm}"

        let serverVols =
              [ keytabVol
              , kubernetes.Volume::{
                , name = "jaas-conf"
                , configMap = Some (cmVolume "jaas-conf" jaasConfKey)
                }
              ]

        let serverMounts =
              [ keytabMount
              , mount
                  "jaas-conf"
                  "/opt/docker/${jaasConfKey}"
                  (Some jaasConfKey)
              ]

        let server =
              deployment
                deploymentName
                (serverEnvVars principal)
                serverVols
                serverMounts
                (None (List Text))

        in  { apiVersion = "v1"
            , kind = "List"
            , items =
              [ k8s.Deployment server
              , k8s.ConfigMap (jaasConfMap principal)
              , k8s.Service (service deploymentName)
              ]
            }

in  buildServer
