# ClickHouse Console — dossier de travail

Plugin JetBrains (Kotlin) qui lance des requetes ClickHouse par l'interface
HTTP officielle du serveur et affiche le resultat en table.

- Identifiant : `com.tgglabs.clickhouseconsole`
- Version livree : **0.0.1** (`ClickHouseConsole-0.0.1.zip`)
- Depot public : <https://github.com/tgg49099-dotcom/clickhouse-console>
- Licence : MIT

## Ce que contient ce dossier

- `src/main/kotlin/com/tgglabs/clickhouse/core/` — le coeur, sans aucune
  dependance a l'IDE : le client HTTP, le parseur des reponses (`JSON` et
  `TSV`), le petit parseur JSON, le modele du resultat.
- `src/main/kotlin/com/tgglabs/clickhouse/ui/` — la fenetre d'outil
  (panneau de connexion, editeur SQL, table de resultats).
- `src/main/kotlin/com/tgglabs/clickhouse/settings/` — les reglages (hote,
  port, utilisateur, base) et le mot de passe (PasswordSafe de l'IDE).
- `src/test/kotlin/` — les tests unitaires (50 tests) et le test du playground
  public de ClickHouse (reserve a `CLICKHOUSE_PUBLIC=1`).
- `FICHE-PRODUIT.md` — la fiche d'une page : nom, description, mots-cles,
  version gratuite, **une seule** fonction payante prevue.
- `README.md` — la notice du depot public (en anglais, comme l'exige le
  Marketplace).

## Etat

**Rien n'est publie.** Aucun prix n'est affiche, le plugin ne parle pas de
« Pro » : la vente n'existe pas encore. La V0 est compilee, testee et
empaquetee ; la decision de publier appartient a la Direction.

## Reconstruire le ZIP

```bash
export JAVA_HOME=/work/tools/jdk-21.0.13+9
./gradlew clean test buildPlugin
```

Le ZIP sort dans `build/distributions/ClickHouseConsole-0.0.1.zip`.
Le build est reproductible (horodatages des entrees du ZIP supprimes).
