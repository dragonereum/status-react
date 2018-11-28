#include "desktopmenu.h"
#include "bridge.h"
#include "eventdispatcher.h"

#include <QCoreApplication>
#include <QDebug>
#include <QDesktopServices>
#include <QUrl>
#include <QFileOpenEvent>

Q_LOGGING_CATEGORY(MENU, "RCTMenu")

namespace {
struct RegisterQMLMetaType {
  RegisterQMLMetaType() { qRegisterMetaType<DesktopMenu *>(); }
} registerMetaType;
} // namespace

class DesktopMenuPrivate {
public:
  Bridge *bridge = nullptr;
  void createMenu(const QVariantMap& items, double callback);
private:
  void onTriggered(QAction* action);
};

void DesktopMenuPrivate::createMenu(const QStringList& items, double callback) {
  QMenu* menu = new QMenu();
  for (const QString& name : items) {
    menu->addAction(name);
  }
  connect(menu, &QMenu::triggered, &DesktopMenuPrivate::onTriggered);
}

void DesktopMenuPrivate::onTriggered(QAction* action) {
    bridge->invokePromiseCallback(callback, QVariantList{action->text()});
}

DesktopMenu::DesktopMenu(QObject *parent)
    : QObject(parent), d_ptr(new DesktopMenuPrivate) {

  QCoreApplication::instance()->installEventFilter(this);
  connect(this, &DesktopMenu::itemClicked, this, &DesktopMenu::handleURL);
}

DesktopMenu::~DesktopMenu() {
}

void DesktopMenu::setBridge(Bridge *bridge) {
  Q_D(DesktopMenu);
  d->bridge = bridge;
}

QString DesktopMenu::moduleName() { return "DesktopMenu"; }

QList<ModuleMethod *> DesktopMenu::methodsToExport() {
  return QList<ModuleMethod *>{};
}

QVariantMap DesktopMenu::constantsToExport() { return QVariantMap(); }

void DesktopMenu::show(const QStringList& items, double callback) {
  Q_D(DesktopMenu);
  d_ptr->createMenu(items, callback);

}


