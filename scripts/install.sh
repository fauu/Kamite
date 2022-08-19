#!/usr/bin/env bash
ROOT_DIR=$(dirname -- "$(readlink -f -- "$0")")

run_install() {
  printf "Press ENTER to accept. To refuse, type 'n' and press ENTER\n\n"

  read -r -p "Create symlink from ./bin/kamite to /usr/bin/kamite? [Yn] " answer
  if [ "$answer" == "${answer#[Nn]}" ] ; then
    sudo ln -sf "$ROOT_DIR"/bin/kamite /usr/bin/kamite
  fi

  read -r -p "Install icons to /usr/share/icons/hicolor? [Yn] " answer
  if [ "$answer" == "${answer#[Nn]}" ] ; then
    for res in 16 32 48 128 256; do
      sudo install -Dm 644 "$ROOT_DIR"/res/icon/icon-"${res}".png \
                           /usr/share/icons/hicolor/"${res}"x"${res}"/apps/kamite.png
    done
  fi

  read -r -p "Install .desktop file to /usr/share/applications? [Yn] " answer
  if [ "$answer" == "${answer#[Nn]}" ] ; then
    sudo install -Dm 644 "$ROOT_DIR"/res/kamite.desktop /usr/share/applications/kamite.desktop
  fi

  printf "\nDone. To remove the created files, run './install.sh --uninstall'.\n"
}

run_uninstall() {
  printf "Uninstalling Kamite\n"
  printf "Answer the corresponding prompt with 'y' if you want the file removed\n\n"

  sudo rm -i /usr/bin/kamite
  for res in 16 32 48 128 256; do
    sudo rm -i /usr/share/icons/hicolor/"${res}"x"${res}"/apps/kamite.png
  done
  sudo rm -i /usr/share/applications/kamite.desktop
}

if [[ "$1" == "--uninstall" ]] ; then
  run_uninstall
else
  run_install
fi
