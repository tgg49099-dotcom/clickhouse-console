// Le nom du projet Gradle est le nom du ZIP produit par `buildPlugin`
// (mesure : le ZIP s'appelle `<rootProject.name>-<version>.zip`).
// Il porte le nom public du plugin, sans espace ni ponctuation, comme sur
// le Marketplace : "ClickHouse Console" -> ClickHouseConsole-0.0.1.zip.
// C'est ce qui garde coherents les quatre identifiants : le <name> de
// plugin.xml, le xmlId, le nom du ZIP et le libelle de la fenetre d'outil.
rootProject.name = "ClickHouseConsole"
