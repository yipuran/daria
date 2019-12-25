# daria
Excel で記述したデータを Database テーブルに、TRUNCATE して表データ毎、INSERT して commitする。

## Excel の形式
・シート名＝テーブル名

・ヘッダ行（１行目）＝列名

## 特徴
- INSERT 文に含む必要ないカラム（NULLABLE）は、列毎省略したExcelシートにすることができる。
- Excel で記述した、数値、文字列、日付型は Database のテーブルの列の型と厳格にチェックされて実行する。
- 必須カラムはチェックされる。
- Excel複数シートを処理するので一度に複数テーブルのデータセットを処理できる。

## 対応するDatabase
Oracle, MySQL, PostgreSQL, SQLServer, H2

## 対応する Excel バージョン
Excel 2010 以上

[説明](../../wiki)
