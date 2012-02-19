(ns circle.pallet.lxc
  (:require pallet.core
            [pallet.action.directory :as directory]
            [pallet.action.exec-script :as exec-script]
            [pallet.action.package :as package]
            [pallet.action.remote-file :as remote-file])
  (:use [circle.backend.pallet :only (append-file)]))

;; added to /etc/networking/interfaces
(def bridge-networking
  "auto br0
iface br0 inet dhcp
bridge_ports eth0
bridge_stp off
bridge_maxwait 5
post-up /usr/sbin/brctl setfd br0 0")

(def lxc
  (pallet.core/group-spec
   "lxc"
   :phases {:configure (fn [session]
                         (-> session
                             (package/packages :aptitude ["lxc" "debootstrap" "bridge-utils" "dnsmasq"])
                             (directory/directory "/cgroup" :action :create)
                             (append-file "/etc/fstab" "none /cgroup cgroup defaults 0 0")
                             (append-file "/etc/network/interfaces" bridge-networking)
                             (exec-script/exec-script
                              (service networking restart))
                             (directory/directory "~/lxc")
                             (remote-file/remote-file "~/lxc/slave.conf" :local-file "pallet/lxc/slave.conf")
                             (exec-script/exec-script
                              (lxc-create -f "~/lxc/slave.conf" -t ubuntu -n slave))))}))