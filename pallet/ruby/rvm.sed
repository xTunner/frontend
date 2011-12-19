# on Ubuntu (11.04 at least), the ~/.bashrc file contains a line that says
# # If not running interactively, don't do anything
# [ -z "$PS1" ] && return
#
# This means RVM is not loaded for non-login shells, which we would like to avoid.
#
# The sed command is going to find that 'If not running
# interactively' line, and insert the RVM source command ahead of
# that, then delete the line that RVM normally installs.

/running interactively/ {
  i\[[ -s "$HOME/.rvm/scripts/rvm" ]] && source "$HOME/.rvm/scripts/rvm"
}

/scripts\/rvm/ {
  d
}