'use strict';

type MenuItems = Array<{
  text?: string,
  onPress?: ?Function,
}>;

const NativeModules = require('react-native').NativeModules;

class DesktopMenu {

  static show(
    menuItems?: MenuItems
  ): void {
    var itemNames = menuItems.map(i => i.text);
    var itemMap = new Map();
    for (let i = 0; i < menuItems.length; ++i) {
      itemMap.set(menuItems[i].text, menuItems[i].onPress);
    }
    console.warn("###js Before DesktopMenu.show()");
    NativeModules.DesktopMenuManager.show(
        itemNames,
        (name) => {
          console.warn("###js DesktopMenu callback invoked");
          itemMap.get(name).onPress();
        }
    );
  }
}

module.exports = DesktopMenu;
