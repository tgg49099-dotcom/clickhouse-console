# Fiche produit — « ClickHouse Console » (V0, 0.0.1)

Document interne. **Rien n'est publie, aucun prix n'est pose.** Le plugin
lui-meme n'affiche aucun prix et ne parle pas de « Pro » : la vente n'existe
pas encore.

## 1. Nom proposé

**ClickHouse Console**

Vérifié le 13/11/2026 sur l'API publique du Marketplace
(`plugins.jetbrains.com/api/searchPlugins?search=clickhouse&max=20`) : 15
résultats, **aucun** ne porte ce nom. Le seul plugin ClickHouse du
Marketplace est « SQL Dialect for ClickHouse » (id **35242**, 36
téléchargements, **gratuit**, coloration syntaxique seule) — c'est-à-dire
qu'il n'y a **aucune console ClickHouse** sur le Marketplace.

- Identifiant (`xmlId`) : `com.tgglabs.clickhouseconsole`
- Nom du ZIP : `ClickHouseConsole-0.0.1.zip`
- Éditeur : Tgg Labs · Licence : MIT · `since-build` 253 (IntelliJ Platform 2025.3)
- Dépôt public du code source : <https://github.com/tgg49099-dotcom/clickhouse-console>
  (le lien « source code » est **obligatoire** dans une fiche Marketplace)

## 2. Description (une phrase, pour le Marketplace)

> Run ClickHouse queries straight from your IDE and read the result as a
> table, over the official HTTP interface of your server.

## 3. Mots-clés proposés

`clickhouse` `database` `sql` `query` `console` `http` `analytics` `olap` `data`

## 4. Ce que fait la version gratuite (la V0 livrée)

- Une fenêtre d'outil **« ClickHouse Console »** : hôte, port, utilisateur, mot
  de passe, base, TLS.
- Un **éditeur SQL** et un bouton **Run** (exécution hors du thread de
  l'interface), plus un bouton **Test connection** (`SELECT version()`).
- La **table de résultat** : les noms de colonnes et les **types** sont ceux
  que le serveur a envoyés ; une valeur SQL `NULL` est affichée `NULL`.
- L'**interface HTTP officielle** : la requête part dans le corps d'un `POST`,
  les identifiants dans les en-têtes `X-ClickHouse-User` / `X-ClickHouse-Key`,
  la base dans `X-ClickHouse-Database`, le format de la réponse dans
  `default_format`. Deux formats de réponse sont lus : `JSON` et `TSV`.
- Le mot de passe est dans le **PasswordSafe** de l'IDE, jamais dans le
  fichier de réglages, jamais dans le code.
- **Aucune** bibliothèque tierce n'est embarquée (ni Gson, ni Jackson, ni
  kotlin-stdlib) : le ZIP ne contient que le plugin.

## 5. UNE seule fonction payante prévue

> **Requêtes enregistrées + historique des requêtes** (par serveur et par
> projet) : enregistrer une requête sous un nom, la rappeler en un clic, et
> retrouver l'historique de ce qui a été exécuté.

Rien d'autre. La version gratuite ci-dessus n'est **jamais** amputée : le
palier payant s'ajoute par-dessus.

**Prix : pas posé.** Le prix et le statut de commerçant (`isTrader`) relèvent
du propriétaire (Tgg), pas de moi. Je ne pose aucun prix.

## 6. Preuve de demande (mesures du 13/11/2026)

| Mesure | Valeur | Source |
|---|---|---|
| Requêtes d'un pilote ClickHouse en Python | **12 118 997** téléchargements/mois | `pypistats.org`, 13/11/2026 |
| Extension VS Code « SQLTools ClickHouse Driver » | **58 217** installations | API VS Code, 13/11/2026 |
| Plugins ClickHouse sur le Marketplace JetBrains | 1 seul, et c'est de la **coloration syntaxique** (36 téléchargements) | API Marketplace, 13/11/2026 |
| Console ClickHouse sur le Marketplace JetBrains | **aucune** | idem |

**Qui est visé, et pourquoi DataGrip n'est pas la cible** : DataGrip a une
page ClickHouse (`jetbrains.com/help/datagrip/clickhouse.html`, HTTP 200 le
13/11/2026) — DataGrip est un IDE *payant et dédié*. Le plugin vise donc les
IDE **IDEA / PyCharm / WebStorm Community**, là où la boîte à outils
« Database Tools » n'existe pas.

## 7. Concurrence (relevée le 13/11/2026)

| Plugin | id | Téléchargements | Prix |
|---|---|---|---|
| SQL Dialect for ClickHouse (coloration seule) | 35242 | 36 | gratuit |
| Database Tools and SQL for WebStorm (DataGrip embarqué) | 10925 | 1 090 429 | gratuit |
| Databee: Database Client & SQL (console multi-SGBD) | 19161 | 142 414 | **payant** |
| Database Plus | 32758 | 300 | **payant** |

Le modèle « console pour un backend » est **prouvé** sur le Marketplace
(9 plugins payants, médiane **156 577** téléchargements payants, mesurés par
la Direction le 13/11/2026). Notre case : **ClickHouse**, qui n'a pas de
console.

## 8. Ce que le plugin ne fait pas

- Il ne modifie rien sur le serveur : il exécute la requête que l'on écrit.
- Il ne parle pas au protocole natif (port 9000) : uniquement à l'interface
  HTTP (8123).
- Il n'envoie rien ailleurs : la requête va au serveur configuré, et nulle
  part ailleurs. Aucun compte, aucune télémétrie.
- Il n'affiche **ni prix ni « Pro »** : la vente n'est pas en ligne.

## 9. Ce qui reste à faire avant une publication

1. Revue de code du jury **sans bloquant** (obligatoire avant tout upload).
2. Relecture des textes visibles par l'utilisateur (description, change notes,
   README) par la pompe à carburant → `/work/carburant/`.
3. Décision de Tgg : statut de commerçant (`isTrader`) et prix.
4. Trois captures d'écran de la fenêtre d'outil (le Marketplace les demande).
