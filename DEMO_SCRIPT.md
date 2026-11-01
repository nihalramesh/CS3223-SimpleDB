# SimpleDB+ Demo Script

This walks through our changes **in change-log order**: Lab 1 → Lab 7, then the two bonuses.
Each step builds on the previous one.

Each step has the same parts:

- **Command:** paste into SimpleIJ, one line at a time
- **Expected output:** what you should see
- **Say:** what to say (put it in your own words)
- **Code:** the file to have open if they ask to see code

Every query and output below was run against a fresh `studentdb` on `main`.

**Time plan (about 16 min, then questions)**

| Step | Topic | ~Time |
|---|---|---|
| 0 | Setup and the dataset | 1.5 min |
| 1 | Lab 1: Non-equality predicates | 1 min |
| 2 | Lab 2: Hash and B+-tree indexes | 2 min |
| 3 | Lab 3: ORDER BY | 1 min |
| 4 | Lab 4: Merge join and nested-loop join (3-way joins) | 3 min |
| 5 | Lab 5A: Hash join | 0.5 min |
| 6 | Lab 5B: Aggregates and GROUP BY | 1.5 min |
| 7 | Lab 6: DISTINCT | 1 min |
| 8 | Lab 6B: EXPLAIN | 1 min |
| 9 | Lab 7: Join experiments | 1 min |
| 10 | Bonus 1: Query error checking | 1.5 min |
| 11 | Bonus 2: DOUBLE type | 1.5 min |
| — | Wrap-up and Q&A | — |

---

## Before the demo (10 minutes early)

1. Eclipse: select both projects → **F5** → **Project → Clean… → Clean all projects**. Check that the Problems view shows no errors.
2. Delete the old `SimpleDBClients/studentdb` folder so the data matches this script.
3. Run `embedded.CreateStudentDB`.
4. Run `SimpleIJ`, type `jdbc:simpledb:studentdb`, and leave it at `SQL>`.

> **Do not run these two queries during the demo:**
> - `explain select eid, sectionid, grade from enroll where studentid = 4` shows `blocks=-2147483645` (B-tree cost bug on tiny tables).
> - `select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear <= yearoffered order by title` crashes with a `NullPointerException` (MergeJoinScan bug).
>
> Neither fix is in the code yet. See "Known issues" at the bottom.

---

## Step 0: Our database (what's in it)

**Say:** *"All demos use the standard `studentdb` from `CreateStudentDB`. It has 5 tables, the biggest with 9 rows, so it's under the 100-tuple limit. We added two indexes in Lab 2: a **hash** index on `student.majorid` and a **B+-tree** index on `enroll.studentid`."*

**Command:** Show the data (optional; you can show the tables below on a slide instead):
```sql
select sid, sname, majorid, gradyear from student
select did, dname from dept
select cid, title, deptid from course
select sectid, courseid, prof, yearoffered from section
select eid, studentid, sectionid, grade from enroll
```

**Expected output:** The data:

```
STUDENT (9 rows, hash index on majorid)     DEPT (3 rows)
 sid sname majorid gradyear                  did dname
   1 joe     10     2021                      10 compsci
   2 amy     20     2020                      20 math
   3 max     10     2022                      30 drama
   4 sue     20     2022
   5 bob     30     2020                    COURSE (6 rows)
   6 kim     20     2020                     cid title       deptid
   7 art     30     2021                      12 db systems    10
   8 pat     20     2019                      22 compilers     10
   9 lee     10     2021                      32 calculus      20
                                              42 algebra       20
SECTION (5 rows)                              52 acting        30
 sectid courseid prof     yearoffered         62 elocution     30
   13     12     turing     2018
   23     12     turing     2019            ENROLL (6 rows, B+-tree index on studentid)
   33     32     newton     2019             eid studentid sectionid grade
   43     32     einstein   2017              14     1        13      A
   53     62     brando     2018              24     1        43      C
                                              34     2        43      B+
                                              44     4        33      B
                                              54     4        53      A
                                              64     6        53      A
```

**Say:** **How every query runs** (one sentence each):
*"SimpleIJ sends the SQL to the Planner. The **Parser** turns it into a QueryData object, the **QueryVerifier** checks it against the catalog (our bonus), and the **HeuristicQueryPlanner** builds a tree of Plan objects. Opening the plan gives a tree of Scans, and we pull rows from the top. I'll use `explain` along the way to show the plan tree. I'll explain how it works in Lab 6B."*

---

## Step 1 — Lab 1: Non-equality predicates

**What we changed:** the Lexer now reads `<  <=  >  >=  !=  <>` (`matchOpr` / `eatOpr`). `Term` stores the operator, and `isSatisfied()` uses `Constant.compareTo()`, so it works on ints and strings.
**Code:** `parse/Lexer.java`, `query/Term.java`

**Command:**
```sql
select sname, gradyear from student where gradyear >= 2021
```
**Expected output:** joe 2021, max 2022, sue 2022, art 2021, lee 2021

**Command:** Strings work too:
```sql
select sname from student where sname < 'c'
```
**Expected output:** amy, bob, art

**Command:**
```sql
select eid, grade from enroll where grade <> 'A'
```
**Expected output:** 24 C, 34 B+, 44 B

**Command:**
```sql
explain select sname, gradyear from student where gradyear >= 2021
```
**Expected output:**
```
ProjectPlan(fields=[sname, gradyear])  [blocks=1, records=4]
  SelectPlan(predicate=gradyear>=2021)  [blocks=1, records=4]
    TablePlan(table=student)  [blocks=1, records=9]
```

**How to read a plan** (bottom-up, each line feeds the one above it):
- `TablePlan`: reads every row of `student` (9 rows, 1 block).
- `SelectPlan`: the WHERE clause. It keeps only rows with `gradyear >= 2021`.
- `ProjectPlan`: the SELECT list. It keeps only the `sname` and `gradyear` columns.
- `[blocks, records]` are the planner's *estimates* (disk blocks read, rows output), not actual counts. For a non-equality condition it guesses that half the rows match (9 / 2 = 4). The real answer is 5 rows.

**Say:** *"Originally SimpleDB only supported `=`. The lexer now reads all comparison operators, and each term compares the two sides with compareTo. For non-equality the planner estimates that half the rows match (9 → 4)."*

---

## Step 2 — Lab 2: Hash and B+-tree indexes

**What we changed:** `create index ... using hash | btree` (hash is the default). The type is stored in a new `indextype` column of the `idxcat` catalog, and `IndexInfo` opens the right class. We also switched to the `HeuristicQueryPlanner` + `IndexUpdatePlanner`, which actually use indexes.
**Code:** `metadata/IndexInfo.java`, `metadata/IndexMgr.java`, `parse/Parser.java`, `embedded/CreateStudentDB.java`

**2a. Both index types are in the catalog**

**Command:**
```sql
select indexname, tablename, fieldname, indextype from idxcat
```
**Expected output:**
```
majoridindex    student  majorid    hash
studentidindex  enroll   studentid  btree
```

**2b. Without vs with an index** (same query shape: `gradyear` has no index, `majorid` has one)

**Command:**
```sql
explain select sname, majorid, gradyear from student where gradyear = 2020
```
**Expected output:** Plain scan:
```
ProjectPlan(fields=[sname, majorid, gradyear])  [blocks=1, records=2]
  SelectPlan(predicate=gradyear=2020)  [blocks=1, records=2]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Command:**
```sql
select sname, majorid, gradyear from student where majorid = 20
```
**Expected output:** `index on majorid used` → amy, sue, kim, pat

**Command:**
```sql
explain select sname, majorid, gradyear from student where majorid = 20
```
**Expected output:** There's now an `IndexSelectPlan`:
```
ProjectPlan(fields=[sname, majorid, gradyear])  [blocks=2, records=2]
  SelectPlan(predicate=majorid=20)  [blocks=2, records=2]
    IndexSelectPlan(value=20)  [blocks=2, records=2]
      TablePlan(table=student)  [blocks=1, records=9]
```

**2c. B+-tree index select** (do **not** run `explain` on this one)

**Command:**
```sql
select eid, sectionid, grade from enroll where studentid = 4
```
**Expected output:** `index on studentid used` → 54 53 A, 44 33 B

**2d. Index join**

**Command:**
```sql
select sname, grade from student, enroll where sid = studentid and gradyear = 2022
```
**Expected output:** sue A, sue B

**Command:**
```sql
explain select sname, grade from student, enroll where sid = studentid and gradyear = 2022
```
**Expected output:**
```
ProjectPlan(fields=[sname, grade])  [blocks=7, records=1]
  SelectPlan(predicate=sid=studentid)  [blocks=7, records=1]
    IndexJoinPlan(field=sid)  [blocks=7, records=4]
      SelectPlan(predicate=gradyear=2022)  [blocks=1, records=2]
        TablePlan(table=student)  [blocks=1, records=9]
      TablePlan(table=enroll)  [blocks=1, records=6]
```

**Say:** *"Hash index: 100 buckets, and a lookup reads only bucket hash(key) mod 100. B+-tree: a lookup walks from the root down to the leaf holding the key. When a query has `indexedfield = constant`, the planner uses an IndexSelectPlan. In a join, for each outer row it looks up matches through the inner table's index instead of scanning it. That's the IndexJoinPlan."*

---

## Step 3 — Lab 3: ORDER BY (multiple fields, ASC/DESC)

**What we changed:** the parser reads `order by f1 [asc|desc], f2 [asc|desc], ...`. `SortPlan` takes a list of directions, and `RecordComparator` flips the sign of the comparison for DESC fields.
**Code:** `parse/Parser.java` (`orderList`), `materialize/RecordComparator.java`, `materialize/SortPlan.java`

**Command:**
```sql
select sname, majorid, gradyear from student order by majorid, gradyear desc
```
**Expected output:** majorid ascending, and within each majorid, gradyear descending:
```
max 10 2022 | lee 10 2021 | joe 10 2021 | sue 20 2022 | kim 20 2020 | amy 20 2020 | pat 20 2019 | art 30 2021 | bob 30 2020
```

**Command:**
```sql
explain select sname, majorid, gradyear from student order by majorid, gradyear desc
```
**Expected output:** `SortPlan(fields=[majorid, gradyear], directions=[true, false])` (true = asc, false = desc)

**Say:** *"Each sort field has its own direction. The comparator goes field by field and flips the result for descending ones."*

---

## Step 4 — Lab 4: Join algorithms (sort-merge and nested-loop), 3-way joins

**What we changed:** `TablePlanner.makeJoinPlan` now tries, in order: **index join** → **sort-merge join** → **nested-loop join** (new `NestedLoopJoinPlan`/`Scan`, which work with any operator). We also fixed RecordComparator, which wasn't sorting ascending fields, and added the ORDER BY step to the HeuristicQueryPlanner.
**Code:** `opt/TablePlanner.java`, `materialize/MergeJoinScan.java`, `query/NestedLoopJoinScan.java`

**4a. 3-way equality join + ORDER BY → sort-merge join**

**Command:**
```sql
select sname, prof, grade from student, enroll, section where sid = studentid and sectionid = sectid order by sname asc, grade desc
```
**Expected output:**
```
mergejoin on sectid=sectionid used (est. cost 6 vs hashjoin 6)
mergejoin on studentid=sid used (est. cost 7 vs hashjoin 7)
amy einstein B+ | joe einstein C | joe turing A | kim brando A | sue newton B | sue brando A
```

**Command:**
```sql
explain select sname, prof, grade from student, enroll, section where sid = studentid and sectionid = sectid order by sname asc, grade desc
```
**Expected output:**
```
ProjectPlan(fields=[sname, prof, grade])
  SortPlan(fields=[sname, grade], directions=[true, false])
    SelectPlan(predicate=sid=studentid)
      MergeJoinPlan(fields=studentid=sid)
        SelectPlan(predicate=sectionid=sectid)
          MergeJoinPlan(fields=sectid=sectionid)
            TablePlan(table=section)
            TablePlan(table=enroll)
        TablePlan(table=student)
```

**Say:** *"The planner starts with the smallest table, section, then adds enroll, then student. There's no index on these join fields, so it uses a sort-merge join: sort both sides on the join key and walk them together like merging two sorted lists."*

**4b. Equality joins + non-equality filters (selection pushdown)**

**Command:**
```sql
select sname, prof, yearoffered, grade from student, enroll, section where sid = studentid and sectionid = sectid and yearoffered < 2019 order by yearoffered desc, sname
```
**Expected output:** joe turing 2018 A, kim brando 2018 A, sue brando 2018 A, amy einstein 2017 B+, joe einstein 2017 C

**Command:**
```sql
explain select sname, prof, yearoffered, grade from student, enroll, section where sid = studentid and sectionid = sectid and yearoffered < 2019 order by yearoffered desc, sname
```
**Expected output:** Look at the bottom of the tree: `SelectPlan(predicate=yearoffered<2019)` sits directly on `TablePlan(table=section)`.

**Say:** *"A filter that mentions only one table is applied to that table before the join, so fewer rows go into the join."*

**4c. Non-equality *join* → nested-loop join** (three techniques in one plan)

**Command:**
```sql
select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear > yearoffered and majorid = 30 order by sname, yearoffered desc
```
**Expected output:**
```
index on majorid used
nestedloopjoin used
mergejoin on courseid=cid used (est. cost 9 vs hashjoin 9)
```
then 10 rows: art 2021 × 5 sections, then bob 2020 × 5 sections, each with yearoffered 2019, 2019, 2018, 2018, 2017.

**Say:** *"Here the join itself is an inequality: graduation year > year offered. Hash, merge and index joins all need equality, so the planner falls back to nested-loop join, which rescans the inner side for each outer row and tests any predicate. One query uses three techniques: the hash index for majorid = 30, a nested-loop join for the inequality, and a merge join for courseid = cid."* (Show the plan in Step 8.)

---

## Step 5 — Lab 5A: Partition-based hash join

**What we changed:** new `HashJoinPlan` / `HashJoinScan`. Both inputs are split into (buffers − 1) partitions by hash of the join value. Then, for each partition, the smaller side is loaded into an in-memory HashMap and the other side probes it. `makeEquiJoin()` estimates the cost of **both** hash and merge join and picks the cheaper one.
**Code:** `materialize/HashJoinPlan.java`, `materialize/HashJoinScan.java`, `opt/TablePlanner.java` (`makeEquiJoin`)

**Command:** Nothing new to type. Point back at the message from 4a:
```
mergejoin on sectid=sectionid used (est. cost 6 vs hashjoin 6)
```

**Say:** *"For every equality join without an index, the planner costs both hash join and merge join. On our 1-block tables they tie, and hash join has to be strictly cheaper to win, so merge is used. On the big Lab 7 database the planner picks hash join on its own. I'll show those numbers in Step 9."*

---

## Step 6 — Lab 5B: Aggregates, with and without GROUP BY

**What we changed:** `count, sum, avg, min, max` in SELECT and ORDER BY (output names like `countofsid`), plus an optional `group by`. `GroupByPlan` sorts on the group fields, then `GroupByScan` produces one row per group. With no GROUP BY, the whole table is one group and there's no sort. MIN/MAX also work on strings. `checkGrouping()` rejects non-aggregated fields that aren't grouped.
**Code:** `parse/Parser.java`, `materialize/GroupByPlan.java`, `materialize/GroupByScan.java`, `materialize/AvgFn.java`

**Without GROUP BY**

**Command:**
```sql
select count(sid), min(gradyear), max(gradyear), avg(gradyear) from student
```
**Expected output:** `9  2019  2022  2020.6667`

**Command:**
```sql
select count(eid), min(grade), max(grade) from enroll
```
**Expected output:** `6  A  C` (MIN/MAX on a varchar)

**With GROUP BY**

**Command:**
```sql
select majorid, count(sid), min(gradyear), max(gradyear), avg(gradyear) from student group by majorid
```
**Expected output:**
```
10  3  2021  2022  2021.3333
20  4  2019  2022  2020.25
30  2  2020  2021  2020.5
```

**GROUP BY over a join, ordered by the aggregate**

**Command:**
```sql
select dname, count(sid) from student, dept where majorid = did group by dname order by countofsid desc
```
**Expected output:** math 4, compsci 3, drama 2

**Command:**
```sql
explain select prof, count(eid) from section, enroll where sectid = sectionid group by prof order by countofeid desc, prof
```
**Expected output:**
```
ProjectPlan(fields=[prof, countofeid])
  SortPlan(fields=[countofeid, prof], directions=[false, true])
    GroupByPlan(fields=[prof])
      SelectPlan(predicate=sectid=sectionid)
        MergeJoinPlan(fields=sectid=sectionid)
          TablePlan(table=section)
          TablePlan(table=enroll)
```

**Say:** *"Without GROUP BY the whole table is one group, so we get one row. With GROUP BY, rows are sorted on the group field, and GroupByScan walks one group at a time, updating every aggregate as it goes. It works on top of joins, and you can order by the aggregate's name, like countofsid."*

---

## Step 7 — Lab 6: DISTINCT

**What we changed:** `select distinct ...` wraps the final projection in a `DistinctPlan`. `DistinctScan` keeps a HashSet of rows already returned and skips repeats, so it doesn't sort and keeps the input order.
**Code:** `plan/DistinctPlan.java`, `query/DistinctScan.java`

**Command:** First, the join creates duplicates:
```sql
select dname from dept, student where did = majorid
```
**Expected output:** 9 rows: compsci ×3, math ×4, drama ×2

**Command:**
```sql
select distinct dname from dept, student where did = majorid
```
**Expected output:** compsci, math, drama

**Command:**
```sql
explain select distinct dname from dept, student where did = majorid
```
**Expected output:**
```
DistinctPlan  [blocks=7, records=1]
  ProjectPlan(fields=[dname])
    SelectPlan(predicate=did=majorid)
      IndexJoinPlan(field=did)
        TablePlan(table=dept)
        TablePlan(table=student)
```

**Say:** *"The join gives one row per student, so department names repeat. DISTINCT sits **above** the projection, because duplicates only appear once the other columns are dropped. It uses a hash set: one pass, no temp tables, and the original order is kept, which is why the output isn't alphabetical."*

---

## Step 8 — Lab 6B: EXPLAIN (displaying the query plan)

**What we changed:** every Plan has `children()` and `description()`. `Planner.explainQueryPlan()` builds the plan **without running it**, and `PlanPrinter` prints the tree recursively with each node's estimated `[blocks, records]`. SimpleIJ treats any line starting with `explain` this way, over both embedded and network JDBC.
**Code:** `plan/PlanPrinter.java`, `plan/Planner.java`, `SimpleDBClients/src/SimpleIJ.java`

**Command:** The showcase plan from Step 4c:
```sql
explain select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear > yearoffered and majorid = 30 order by sname, yearoffered desc
```
**Expected output:**
```
ProjectPlan(fields=[sname, gradyear, title, yearoffered])
  SortPlan(fields=[sname, yearoffered], directions=[true, false])
    SelectPlan(predicate=courseid=cid)
      MergeJoinPlan(fields=courseid=cid)                      <- equality join
        NestedLoopJoinPlan(predicate=gradyear>yearoffered)    <- non-equality join
          SelectPlan(predicate=majorid=30)
            IndexSelectPlan(value=30)                         <- hash index
              TablePlan(table=student)
          TablePlan(table=section)
        TablePlan(table=course)
```

**Say:** *"We've been using this all along. EXPLAIN builds the same plan the query would use, doesn't execute it, and prints the tree with the planner's cost estimates. The sorts used internally by merge join and GROUP BY are hidden, so the only SortPlan you see is the ORDER BY."*

---

## Step 9 — Lab 7: Join experiments (talk only, don't run live)

**What we changed:**
- `JoinMethods`: restricts which join methods the planner may use, so the same query can be forced into different plans.
- `CreateExperimentDB`: builds `expdb` (20 depts, 2000 students, 100 courses, 400 sections, 8000 enrollments) from a fixed random seed, with B-tree indexes.
- `JoinExperiment`: runs each query once per join method, 3 times each.
- Bugs fixed along the way: B-tree buffer leaks (`BTPage`, `BTreeIndex`), and `FileMgr` running out of file handles (it now keeps at most 500 files open).

**Code:** `opt/JoinMethods.java`, `experiment/JoinExperiment.java`

Results (average ms, from our earlier run):

| Query | index | hash | merge | nested loop |
|---|---|---|---|---|
| 2-table (student ⋈ enroll) | **36** | 384 | 5793 | 6351 |
| 4-table | 1610\* | **1567** | 18240 | 8493 |

\* With only index join allowed, 2 of the 3 joins fall back to nested loop, because there's no index on their inner side.

**Say:** *"`expdb` is over the 100-tuple limit, so we're not running it live. Index join wins when an index fits the join. Hash join is the best general-purpose equality join. Merge join was slowest here, because writing and merging the sorted temp tables dominates its cost."*

---

## Step 10 — Bonus 1: Query error checking

**Before:** `verifyQuery()`/`verifyUpdate()` were empty. An unknown table crashed with a NullPointerException, a WHERE on a misspelled field was silently ignored (so you got every row), and an over-long string overwrote the next record.
**Now:** `QueryVerifier` checks every statement against the catalog **before planning**. On failure it throws a `SemanticException` naming the problem, and the transaction is rolled back.
**Code:** `plan/QueryVerifier.java`, `plan/Planner.java`

**Command:** Paste each one. They should all be rejected:

| Command | Expected output |
|---|---|
| `select sname from studnt` | table studnt does not exist |
| `select sname from student where gpa = 3` | field gpa does not exist in student |
| `select sname from student where sname = 10` | cannot compare sname (varchar) with 10 (int) |
| `select sname from student, student` | table student appears more than once in the from clause |
| `insert into dept(did, dname) values (40, 'engineering')` | value 'engineering' is too long for field dname (varchar(8)) |
| `insert into dept(did, dname) values ('x', 'art')` | cannot store 'x' (varchar) in field did (int) |
| `insert into dept(did) values (50, 'art')` | insert into dept lists 1 fields but 2 values |
| `update student set sname = gradyear where sid = 2` | cannot set sname (varchar) to gradyear (int) |
| `select sum(sname) from student` | cannot sum non-numeric field sname |
| `create index majoridindex on student(majorid)` | field majorid of student is already indexed |
| `select sname, count(sid) from student` | field sname must appear in the group by clause or be aggregated *(from the parser, Lab 5B)* |

**Command:** Prove nothing was half-done:
```sql
select count(did) from dept
```
**Expected output:** `3` (the bad inserts didn't go in)

*Short on time? Do `studnt`, `gpa = 3`, `'engineering'` and `count(sid)` only.*

**Say:** *"Each error names exactly what's wrong, and the transaction is rolled back. The `gpa = 3` case is the important one: before, it silently returned every student."*

---

## Step 11 — Bonus 2: DOUBLE data type

**What we changed:** `double` is a new column type, added through every layer: 8 bytes in a record (`Layout`, `Page.get/setDouble`), the lexer reads `39.99` as one token, a `SetDoubleRecord` log record for rollback/recovery, comparisons by value (`8 = 8.0`, and they hash the same, so indexes and hash join match), `SUM` of a double is a double, `AVG` always returns a double, `getDouble()` over JDBC, and SimpleIJ prints at most 4 decimal places.
**Code:** `record/Layout.java`, `file/Page.java`, `parse/Lexer.java`, `query/Constant.java`, `tx/recovery/SetDoubleRecord.java`

**Command:**
```sql
create table product(pid int, pname varchar(10), price double)
insert into product(pid, pname, price) values (1, 'pen', 1.25)
insert into product(pid, pname, price) values (2, 'notebook', 4.5)
insert into product(pid, pname, price) values (3, 'backpack', 39.99)
insert into product(pid, pname, price) values (4, 'mug', 8)
```
**Expected output:** Each one prints `1 records processed`. Note that the mug's price is the **int** 8.

**Command:** Comparison + sort:
```sql
select pname, price from product where price > 4.25 order by price desc
```
**Expected output:** backpack 39.99, mug 8.0, notebook 4.5

**Command:** Int matches double by value:
```sql
select pname, price from product where price = 8
```
**Expected output:** mug 8.0

**Command:** Aggregates:
```sql
select sum(price), avg(price), min(price), max(price) from product
```
**Expected output:** `53.74  13.435  1.25  39.99`

**Command:** Update:
```sql
update product set price = 9.75 where pid = 4
select pname, price from product where price = 9.75
```
**Expected output:** mug 9.75

**Command:** Type rules (ties back to Bonus 1):
```sql
insert into student(sid, sname, majorid, gradyear) values (99, 'zed', 10, 2020.5)
```
**Expected output:** `cannot store 2020.5 (double) in field gradyear (int)`: an int fits in a double column, but not the other way round, because the fraction would be lost.

**Say:** *"Doubles work everywhere ints do: comparisons, sorting, aggregates, indexes and joins. Ints and doubles compare by value, and 8 and 8.0 hash the same, so they also match in hash indexes and hash joins."*

---

## Wrap-up

**Say:** *"To summarise: non-equality predicates, hash and B+-tree indexes used for selections and joins, multi-field ORDER BY, three new join algorithms chosen by cost, aggregates with and without GROUP BY, DISTINCT, EXPLAIN, the join experiments, and two bonuses: error checking and a DOUBLE type. Happy to take questions."*

---

## Likely questions

- **Why merge join instead of hash join on studentdb?** With no index, the planner costs both. On 1-block tables they tie (6 vs 6), and hash join only wins when strictly cheaper. On expdb it picks hash join.
- **How is the join order chosen?** Start with the table that has the fewest rows after its filters, then repeatedly add the table that gives the smallest join result (a left-deep tree).
- **The index plan estimates 2 blocks and the scan 1. Why use the index?** student fits in one block, so a scan is already 1. The heuristic always uses an index for `field = constant`, because on bigger tables the scan grows with the table while the index stays at about one lookup.
- **Why doesn't plain `student, enroll` use the B-tree?** Without the gradyear filter, enroll (6) is smaller than student (9), so enroll goes on the outside. The index is on enroll, so it can't be used for lookups.
- **Why does `gradyear >= 2021` estimate 4 rows when there are 5? Is it always half?** Yes. For any non-equality condition, `Term.reductionFactor()` returns a fixed factor of 2, so the planner estimates half the rows (9 / 2 = 4, integer division). It doesn't look at the value or the operator: `> 2021` (actually 2 rows), `<= 2019` (1) and `!= 2020` (6) are all estimated as 4. Several conditions multiply (two conditions → divide by 4). SimpleDB keeps no histograms or min/max statistics, so it can't know how many values fall in a range, and half is a simple rule of thumb. It only affects the estimate used to compare plans. The results are always correct, because every row is actually checked.
- **Why do some EXPLAIN nodes show records=0?** The join predicate is applied again as a SelectPlan above the join, so the estimate is divided twice. The actual results are correct; only the estimate is off.
- **Why must an index exist before inserting rows?** Index entries are only added on insert (`IndexUpdatePlanner`). There's no bulk build from existing rows.
- **Hash vs B-tree?** Hash: one bucket per lookup, equality only. B-tree: root-to-leaf traversal. It could do ranges, but our planner only uses indexes for equality.
- **Why no `student.sid`?** SimpleDB has no table qualifiers. A field name that appears in two FROM tables is reported as ambiguous by the verifier.
- **Bugs you fixed?** RecordComparator didn't sort ascending fields (Lab 4). SortScan's save/restore position skipped rows on duplicate merge-join keys (Lab 5A). B-tree buffer leaks and too many open files on expdb (Lab 7).

---

## Known issues (not yet fixed in the code)

1. **MergeJoinScan crash:** `beforeFirst()` doesn't reset `joinval`, so a merge join that gets restarted (for example, under a nested-loop join) crashes. Repro: the `gradyear <= yearoffered order by title` query in the warning box at the top.
2. **B-tree cost on tiny tables:** `BTreeIndex.searchCost` takes `log(0)` when the index has 0 blocks, so EXPLAIN shows about −2 billion blocks. Repro: `explain select eid, sectionid, grade from enroll where studentid = 4`.
