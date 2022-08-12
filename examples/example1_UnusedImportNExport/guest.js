vie = 3
Polyglot.export("vie", vie)
Polyglot.import('NotExistedVariable')


Polyglot.import("Import Before Export")
Polyglot.export('Import Before Export', "value")


vie = Polyglot.import("vie")