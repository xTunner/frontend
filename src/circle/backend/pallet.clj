(ns circle.backend.pallet
  "fns for working with pallet"
  (:require
   clojure.string
   [pallet.action.exec-script :as exec-script]
   [pallet.stevedore :as stevedore]
   [pallet.action.remote-file :as remote-file]))

(defn flags->rvmrc [flags]
  (apply str
         (map
          (fn [[k v]]
            (str
             "export "
             (name k)
             "="
             (if (string? v) (str "\"" v "\"") v)
             "\n"))
          flags)))

;; A complete list of rvm flags, from grepping the source for _flag using:
;; $ grep -oh"\w*flag\w*" -R * | sort | uniq
;; rvm_18_flag
;; rvm_19_flag
;; rvm_all_flag
;; rvm_always_trust_rvmrc_flag
;; rvm_archflags
;; rvm_archive_flag
;; rvm_auto_flag
;; rvm_autoconf_flags
;; rvm_benchmark_flag
;; rvm_bin_flag
;; rvm_cd_complete_flag
;; rvm_clang_flag
;; rvm_clear_flag
;; rvm_configure_flags
;; rvm_create_flag
;; rvm_debug_flag
;; rvm_default_flag
;; rvm_delete_flag
;; rvm_dir_flag
;; rvm_docs_flag
;; rvm_dump_environment_flag
;; rvm_empty_flag
;; rvm_env_flag
;; rvm_etc_profile_flag
;; rvm_export_flag
;; rvm_force_autoconf_flag
;; rvm_force_flag
;; rvm_gem_flag
;; rvm_gemdir_flag
;; rvm_gems_flag
;; rvm_gemset_create_on_use_flag
;; rvm_head_flag
;; rvm_import_flag
;; rvm_install_flag
;; rvm_install_on_use_flag
;; rvm_interactive_flag
;; rvm_json_flag
;; rvm_latest_flag
;; rvm_list_flag
;; rvm_llvm_flag
;; rvm_loaded_flag
;; rvm_make_flags
;; rvm_make_flags_flag
;; rvm_name_flag
;; rvm_nightly_flag
;; rvm_only_path_flag
;; rvm_path_flag
;; rvm_pretty_print_flag
;; rvm_quiet_flag
;; rvm_reconfigure_flag
;; rvm_reload_flag
;; rvm_remove_flag
;; rvm_ruby_configure_flags
;; rvm_ruby_flag
;; rvm_ruby_selected_flag
;; rvm_rubygems_flag
;; rvm_rvmrc_flag
;; rvm_self_flag
;; rvm_shebang_flag
;; rvm_silent_flag
;; rvm_skel_flag
;; rvm_skip_autoreconf_flag
;; rvm_static_flag
;; rvm_sticky_flag
;; rvm_summary_flag
;; rvm_system_flag
;; rvm_tail_flag
;; rvm_teset_suite_flag
;; rvm_test_flag
;; rvm_test_suite_flag
;; rvm_trace_flag
;; rvm_trust_rvmrcs_flag
;; rvm_uninstall_flag
;; rvm_use_flag
;; rvm_user_flag
;; rvm_user_install_flag
;; rvm_verbose_flag
;; rvm_yaml_flag

(defn install-rvmrc [session flags]
  "Create a .rvmrc file from flags, a map from flags to values"
  (-> session
      (remote-file/remote-file "~/.rvmrc"
                               :content (flags->rvmrc flags)
                               :no-versioning true
                               :owner (-> session :user :username)
                               :group (-> session :user :username)
                               :mode "600")))

(defn install-gemrc [session content]
  "Create a .rvmrc file from flags, a map from flags to values"
  (-> session
      (remote-file/remote-file "~/.gemrc"
                               :content content
                               :no-versioning true
                               :owner (-> session :user :username)
                               :group (-> session :user :username)
                               :mode "600")))

(defmacro user-code
  "Runs a seq of stevedore commands as the non-sudo user"
  [session & cmds]
  `(let [cmds# (stevedore/with-script-language :pallet.stevedore.bash/bash
                 (apply stevedore/chain-commands (map (fn [c#]
                                                        (stevedore/emit-script [c#])) (stevedore/quasiquote ~cmds))))]
    (-> ~session
        (exec-script/exec-checked-script
         (str (quote ~cmds))
         ("sudo" "-H" "-u" (unquote (-> ~session :user :username)) "bash" "-e" "-c" (unquote (format "'%s'" cmds#)))))))