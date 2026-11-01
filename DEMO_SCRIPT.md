# SimpleDB+ Demo Script

Follows our labs in the order we built them, then the bonuses. Each step notes which demo requirement (a–f) it covers.

- **Say:** read out (short on purpose)
- **Run:** paste into SimpleIJ, one line at a time
- **Expect:** what appears
- **If asked:** extra detail. Don't read it out unless someone asks.

All queries and outputs were checked against a fresh `studentdb` on `main`.

| Time | Step | Requirement |
|---|---|---|
| 0:00 | Intro (EXPLAIN, Lab 6B, used throughout) | (f) |
| 1:00 | Lab 1: Non-equality predicates | (c) non-equality |
| 2:00 | Lab 2: B+-tree and hash indexes | (a) |
| 5:00 | Lab 4: 3-way joins, with Lab 3 ORDER BY and Lab 5A hash join | (c) |
| 9:00 | Lab 5B: Aggregates and GROUP BY | (d) |
| 11:00 | Lab 6: DISTINCT | (b) |
| 12:30 | Bonus 1: Error checking, Bonus 2: DOUBLE | (e) |
| 15:00 | Questions (Lab 7 experiments are in Q&A) | |

---

## Before the demo

1. Eclipse: select both projects → **F5** → **Project → Clean… → Clean all projects**. The Problems view should show no errors.
2. Delete the `SimpleDBClients/studentdb` folder.
3. Run `embedded.CreateStudentDB`.
4. Run `SimpleIJ`, type `jdbc:simpledb:studentdb`, and leave it at `SQL>`.

> **Don't run during the demo** (see Known issues):
> - `explain select eid, sectionid, grade from enroll where studentid = 4`
> - `select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear <= yearoffered order by title`

---

## Intro · (f)

**Say:**
> "We'll go through our labs in order, then our two bonuses. We're using the student database: five tables, at most 9 rows each. Putting `explain` in front of a query, which we added in Lab 6B, prints its plan instead of running it, so I'll show plans as we go."

**If asked:**
- Tables: student (9 rows, hash index on majorid), dept (3), course (6), section (5), enroll (6, B+-tree on studentid).
- Query flow: parser → verifier (our bonus) → heuristic planner builds a plan tree → opened as a scan tree → rows pulled from the top.
- **EXPLAIN (Lab 6B):** the planner builds exactly the plan the query would use but doesn't run it. `PlanPrinter` walks the tree and prints each operator with its estimates. Works over embedded and network connections.

---

## Lab 1: Non-equality predicates · (c)

**Say:**
> "Lab 1: SimpleDB only supported `=`. We added `<`, `<=`, `>`, `>=`, `!=` and `<>`."

**Run:**
```sql
select sname, gradyear from student where gradyear >= 2021
explain select sname, gradyear from student where gradyear >= 2021
```
**Expect:**
```
      sname gradyear
--------------------
        joe     2021
        max     2022
        sue     2022
        art     2021
        lee     2021

ProjectPlan(fields=[sname, gradyear])  [blocks=1, records=4]
  SelectPlan(predicate=gradyear>=2021)  [blocks=1, records=4]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "Plans read bottom up: scan the table, filter with our `>=`, then project. The numbers are the planner's estimates."

**If asked:**
- The lexer reads multi-character operators. `Term.isSatisfied()` uses `compareTo`, so it works on ints and strings (e.g. `where grade <> 'A'` → 24 C, 34 B+, 44 B).
- `[blocks, records]` are estimates of disk blocks read and rows output. For a non-equality the planner assumes half the rows match: 9 / 2 = 4, though really 5. Estimates only rank plans; results are always exact.

---

## Lab 2: B+-tree and hash indexes · (a)

**Say:**
> "Lab 2: we added B+-tree indexes alongside hash. The index type is stored in the catalog."

**Run:**
```sql
select indexname, tablename, fieldname, indextype from idxcat
```
**Expect:**
```
        indexname        tablename        fieldname        indextype
--------------------------------------------------------------------
     majoridindex          student          majorid             hash
   studentidindex           enroll        studentid            btree
```

**Say:**
> "Without an index, it's a full table scan."

**Run:**
```sql
explain select sname, majorid, gradyear from student where gradyear = 2020
```
**Expect:**
```
ProjectPlan(fields=[sname, majorid, gradyear])  [blocks=1, records=2]
  SelectPlan(predicate=gradyear=2020)  [blocks=1, records=2]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "On majorid, the hash index is used instead."

**Run:**
```sql
select sname, majorid, gradyear from student where majorid = 20
explain select sname, majorid, gradyear from student where majorid = 20
```
**Expect:**
```
index on majorid used
      sname majorid gradyear
----------------------------
        amy      20     2020
        sue      20     2022
        kim      20     2020
        pat      20     2019

ProjectPlan(fields=[sname, majorid, gradyear])  [blocks=2, records=2]
  SelectPlan(predicate=majorid=20)  [blocks=2, records=2]
    IndexSelectPlan(value=20)  [blocks=2, records=2]
      TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "The B+-tree on enroll works the same way."

**Run:**
```sql
select eid, sectionid, grade from enroll where studentid = 4
```
**Expect:**
```
index on studentid used
    eid sectionid grade
-----------------------
     54        53     A
     44        33     B
```

**Say:**
> "Indexes are also used in joins. Here it's an index join through the B+-tree."

**Run:**
```sql
select sname, grade from student, enroll where sid = studentid and gradyear = 2022
explain select sname, grade from student, enroll where sid = studentid and gradyear = 2022
```
**Expect:**
```
      sname grade
-----------------
        sue     A
        sue     B

ProjectPlan(fields=[sname, grade])  [blocks=7, records=1]
  SelectPlan(predicate=sid=studentid)  [blocks=7, records=1]
    IndexJoinPlan(field=sid)  [blocks=7, records=4]
      SelectPlan(predicate=gradyear=2022)  [blocks=1, records=2]
        TablePlan(table=student)  [blocks=1, records=9]
      TablePlan(table=enroll)  [blocks=1, records=6]
```

**If asked:**
- **How the index is chosen:** you pick `using hash` or `using btree` in CREATE INDEX. The planner always uses an index for `field = constant`.
- **Hash vs B+-tree:** hash goes straight to one of 100 buckets. The B+-tree walks from the root to a leaf. Our planner only uses indexes for equality.
- **Index join:** the filtered students are the outer side. For each one, it looks up their enrollments in the B+-tree, so enroll is never scanned.
- **"The index costs 2 blocks and the scan 1?"** student fits in one block, so a scan can't be cheaper. On big tables the scan grows with the table; the index only reads matching rows.
- **"Why not the index for plain `student, enroll`?"** enroll is smaller, so it becomes the outer side, and the index is on enroll.
- **`blocks=7`:** affected by the B-tree cost bug (Known issue 2). Talk about records instead.
- **Indexes must exist before inserts.** Entries are added on insert; there's no bulk build.

---

## Lab 4: Join algorithms on 3-way joins (with Lab 3 ORDER BY and Lab 5A hash join) · (c)

**Lab 4: merge join** · **Say:**
> "Lab 4: joins without an index used to be a cross product. Now the planner picks a join algorithm. Three tables, two equality joins:"

**Run:**
```sql
select sname, prof, grade from student, enroll, section where sid = studentid and sectionid = sectid
```
**Expect:**
```
mergejoin on sectid=sectionid used (est. cost 6 vs hashjoin 6)
mergejoin on studentid=sid used (est. cost 7 vs hashjoin 7)
      sname     prof grade
--------------------------
        joe einstein     C
        joe   turing     A
        amy einstein    B+
        sue   brando     A
        sue   newton     B
        kim   brando     A
```

**Lab 5A: hash join** · **Say:**
> "Those messages are from Lab 5A: the planner prices a hash join against a merge join and takes the cheaper. Here they tie, so merge join wins."

**Lab 3: ORDER BY** · **Say:**
> "Now add Lab 3's ORDER BY: name ascending, grade descending."

**Run:**
```sql
select sname, prof, grade from student, enroll, section where sid = studentid and sectionid = sectid order by sname asc, grade desc
explain select sname, prof, grade from student, enroll, section where sid = studentid and sectionid = sectid order by sname asc, grade desc
```
**Expect:**
```
      sname     prof grade
--------------------------
        amy einstein    B+
        joe einstein     C
        joe   turing     A
        kim   brando     A
        sue   newton     B
        sue   brando     A

ProjectPlan(fields=[sname, prof, grade])  [blocks=1, records=1]
  SortPlan(fields=[sname, grade], directions=[true, false])  [blocks=1, records=1]
    SelectPlan(predicate=sid=studentid)  [blocks=2, records=1]
      MergeJoinPlan(fields=studentid=sid)  [blocks=2, records=6]
        SelectPlan(predicate=sectionid=sectid)  [blocks=2, records=3]
          MergeJoinPlan(fields=sectid=sectionid)  [blocks=2, records=10]
            TablePlan(table=section)  [blocks=1, records=5]
            TablePlan(table=enroll)  [blocks=1, records=6]
        TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "Two merge joins, and the SortPlan on top has a direction per field: true is ascending, false is descending."

**Lab 4: nested-loop join** · **Say:**
> "Now a non-equality join: gradyear greater than yearoffered."

**Run:**
```sql
select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear > yearoffered and majorid = 30 order by sname, yearoffered desc
explain select sname, gradyear, title, yearoffered from student, section, course where courseid = cid and gradyear > yearoffered and majorid = 30 order by sname, yearoffered desc
```
**Expect:**
```
index on majorid used
nestedloopjoin used
mergejoin on courseid=cid used (est. cost 9 vs hashjoin 9)
      sname gradyear                title yearoffered
-----------------------------------------------------
        art     2021             calculus        2019
        art     2021           db systems        2019
        art     2021            elocution        2018
        art     2021           db systems        2018
        art     2021             calculus        2017
        bob     2020             calculus        2019
        bob     2020           db systems        2019
        bob     2020            elocution        2018
        bob     2020           db systems        2018
        bob     2020             calculus        2017

ProjectPlan(fields=[sname, gradyear, title, yearoffered])  [blocks=1, records=3]
  SortPlan(fields=[sname, yearoffered], directions=[true, false])  [blocks=1, records=3]
    SelectPlan(predicate=courseid=cid)  [blocks=2, records=3]
      MergeJoinPlan(fields=courseid=cid)  [blocks=2, records=10]                      <- equality join
        NestedLoopJoinPlan(predicate=gradyear>yearoffered)  [blocks=4, records=5]     <- non-equality join
          SelectPlan(predicate=majorid=30)  [blocks=2, records=2]
            IndexSelectPlan(value=30)  [blocks=2, records=2]                          <- hash index
              TablePlan(table=student)  [blocks=1, records=9]
          TablePlan(table=section)  [blocks=1, records=5]
        TablePlan(table=course)  [blocks=1, records=6]
```
(The `<-` labels are ours, not part of the output.)

**Say:**
> "An inequality can't use index, hash or merge join, so the planner uses a nested-loop join. This plan has all three: hash index, nested loop and merge join."

**If asked:**
- **Join order:** greedy and left-deep. Start with the smallest table after filters (section), then keep adding the table that gives the smallest join result.
- **Join algorithm:** index join if possible; otherwise, for equality, the cheaper of hash and merge; otherwise nested loop.
- **Why merge, not hash?** On 1-block tables they tie at 6. Merge: sort both (read + write, 2 each) + 2 to merge. Hash: partition both (read 2 + write 2) + 2 to join. Hash must be strictly cheaper to win. On large tables it does (see Lab 7 below).
- **Merge join:** sort both sides on the join field, then walk them together like merging two sorted lists.
- **Hash join (Lab 5A):** hash both sides into partitions so matches land together, then join each partition with an in-memory hash table.
- **Nested loop:** for each outer row, rescan the inner table and test the condition. Works with any operator; cost ≈ outer rows × inner blocks.
- **ORDER BY (Lab 3):** any number of fields, each ASC or DESC (ASC by default). The comparator compares field by field and flips the sign for DESC fields, which is why joe's C comes before his A.
- **Filters with joins:** a filter on one table (e.g. `yearoffered < 2019`) is applied to that table before any join.

---

## Lab 5B: Aggregates and GROUP BY · (d)

**Say:**
> "Lab 5B: aggregates. Without GROUP BY, the whole table is one group."

**Run:**
```sql
select count(sid), min(gradyear), max(gradyear), avg(gradyear) from student
```
**Expect:**
```
 countofsid minofgradyear maxofgradyear avgofgradyear
-----------------------------------------------------
          9          2019          2022     2020.6667
```

**Say:**
> "With GROUP BY, one row per major."

**Run:**
```sql
select majorid, count(sid), min(gradyear), max(gradyear), avg(gradyear) from student group by majorid
explain select majorid, count(sid), min(gradyear), max(gradyear), avg(gradyear) from student group by majorid
```
**Expect:**
```
 majorid countofsid minofgradyear maxofgradyear avgofgradyear
-------------------------------------------------------------
      10          3          2021          2022     2021.3333
      20          4          2019          2022       2020.25
      30          2          2020          2021        2020.5

ProjectPlan(fields=[majorid, countofsid, minofgradyear, maxofgradyear, avgofgradyear])  [blocks=1, records=4]
  GroupByPlan(fields=[majorid])  [blocks=1, records=4]
    TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "It also works on a join, sorted by the aggregate."

**Run:**
```sql
select dname, count(sid) from student, dept where majorid = did group by dname order by countofsid desc
```
**Expect:**
```
    dname countofsid
--------------------
     math          4
  compsci          3
    drama          2
```

**If asked:**
- **How GROUP BY works:** sort on the group fields so each group is contiguous, then walk group by group, updating every aggregate, and output a row when the group changes.
- **No GROUP BY:** no sort; the whole input is one group, so there's always exactly one output row.
- **Output names:** `countofsid`, `avgofgradyear` and so on. You can ORDER BY them.
- **AVG** returns a decimal. **MIN/MAX** also work on strings.

---

## Lab 6: DISTINCT · (b)

**Say:**
> "Lab 6: DISTINCT. This join gives one row per student, so department names repeat."

**Run:**
```sql
select dname from dept, student where did = majorid
```
**Expect:** 9 rows: compsci ×3, math ×4, drama ×2

**Say:**
> "DISTINCT removes them."

**Run:**
```sql
select distinct dname from dept, student where did = majorid
explain select distinct dname from dept, student where did = majorid
```
**Expect:**
```
    dname
---------
  compsci
     math
    drama

DistinctPlan  [blocks=7, records=1]
  ProjectPlan(fields=[dname])  [blocks=7, records=1]
    SelectPlan(predicate=did=majorid)  [blocks=7, records=1]
      IndexJoinPlan(field=did)  [blocks=7, records=6]
        TablePlan(table=dept)  [blocks=1, records=3]
        TablePlan(table=student)  [blocks=1, records=9]
```

**Say:**
> "DistinctPlan sits on top of the projection and uses a hash set to skip repeated rows."

**If asked:**
- **Why above the projection?** Duplicates only appear once the other columns are dropped.
- **Why a hash set, not a sort?** One pass and no temp table. That's why the output keeps its original order instead of being alphabetical.
- **Multiple columns?** The whole row is the key, so `distinct a, b` removes rows where both match.
- This join uses the hash index on majorid.

---

## Bonuses · (e)

**Bonus 1: error checking** · **Say:**
> "Bonus one: a verifier that checks every query against the catalog, so errors give clear messages instead of crashes or wrong results."

**Run** (one at a time):
```sql
select sname from studnt
select sname from student where gpa = 3
insert into dept(did, dname) values (40, 'engineering')
select sname, count(sid) from student
```
**Expect** (each after `transaction N rolled back`):
```
SQL Exception: simpledb.plan.SemanticException: table studnt does not exist
SQL Exception: simpledb.plan.SemanticException: field gpa does not exist in student
SQL Exception: simpledb.plan.SemanticException: value 'engineering' is too long for field dname (varchar(8))
SQL Exception: simpledb.parse.BadSyntaxException: field sname must appear in the group by clause or be aggregated
```

**Bonus 2: DOUBLE type** · **Say:**
> "Bonus two: a DOUBLE data type."

**Run** (one at a time):
```sql
create table product(pid int, pname varchar(10), price double)
insert into product(pid, pname, price) values (1, 'pen', 1.25)
insert into product(pid, pname, price) values (2, 'notebook', 4.5)
insert into product(pid, pname, price) values (3, 'backpack', 39.99)
insert into product(pid, pname, price) values (4, 'mug', 8)
select pname, price from product where price > 4.25 order by price desc
select sum(price), avg(price), min(price), max(price) from product
insert into student(sid, sname, majorid, gradyear) values (99, 'zed', 10, 2020.5)
```
**Expect:**
```
0 records processed            (create)
1 records processed            (each insert)

      pname      price
----------------------
   backpack      39.99
        mug        8.0
   notebook        4.5

 sumofprice avgofprice minofprice maxofprice
--------------------------------------------
      53.74     13.435       1.25      39.99

SQL Exception: simpledb.plan.SemanticException: cannot store 2020.5 (double) in field gradyear (int)
```

**Say:**
> "Doubles work in filters, sorting and aggregates. An int fits in a double column, but not the other way round."

**If asked:**
- **What the verifier fixed:** a wrong table used to crash with a NullPointerException, a wrong field in WHERE was silently ignored (returning every row), and a too-long string overwrote the next record on disk.
- **What it checks:** tables and fields exist, comparison types match, SUM/AVG only on numbers, insert/update types and string lengths, GROUP BY rules, duplicate indexes. Every failure rolls back the transaction.
- **DOUBLE internals:** 8 bytes per value. int and double compare by value, and 8 and 8.0 hash the same, so they match in hash indexes and hash joins. Updates are logged for rollback.

---

## Wrap-up

**Say:**
> "That's our labs in order, from non-equality predicates through DISTINCT and EXPLAIN, plus our two bonuses. Happy to take questions."

---

## Questions about performance · Lab 7

We timed each join method on a generated database (2,000 students, 8,000 enrollments), 3 runs each:

| Query | index | hash | merge | nested loop |
|---|---|---|---|---|
| 2-table | **36 ms** | 384 ms | 5793 ms | 6351 ms |
| 4-table | 1610 ms\* | **1567 ms** | 18240 ms | 8493 ms |

\* 2 of the 3 joins fall back to nested loop when only index join is allowed. Index join wins when there's an index; hash join is the best general equality join.

## Questions about plan numbers

**What are blocks and records?** Estimates, made before the query runs. Only TablePlan's numbers are real: the statistics manager counts every table at startup and every 100 requests. Everything above is a formula:

| Operator | blocks | records |
|---|---|---|
| SelectPlan | same as child | child ÷ 2 for non-equality; child ÷ distinct values for equality |
| IndexSelectPlan | index search cost + 1 per matching row | rows ÷ distinct values |
| IndexJoinPlan | outer blocks + outer rows × search cost + matching rows | outer rows × matches per lookup |
| MergeJoinPlan | the two sorted inputs | left × right ÷ max distinct join values |
| NestedLoopJoinPlan | outer blocks + outer rows × inner blocks | left × right ÷ reduction factor |
| Sort / Project / Distinct | same as child | same as child |
| GroupByPlan | its sorted input | distinct values of the group fields |

Distinct values are guessed as 1 + rows/3. Hash index search cost = index blocks ÷ 100.

**Why are some estimates 0 or 1?** The join condition is applied by the join and again by the SelectPlan above it, so the estimate is divided twice. Results are always correct; estimates are only used to compare plans.

## Where's the code?

| Lab | Files |
|---|---|
| 1: Non-equality | `parse/Lexer.java`, `query/Term.java` |
| 2: Indexes | `metadata/IndexInfo.java`, `metadata/IndexMgr.java`, `index/btree/BTreeIndex.java` |
| 3: ORDER BY | `parse/Parser.java`, `materialize/SortPlan.java`, `materialize/RecordComparator.java` |
| 4: Joins | `opt/TablePlanner.java`, `opt/HeuristicQueryPlanner.java`, `materialize/MergeJoinScan.java`, `query/NestedLoopJoinScan.java` |
| 5A: Hash join | `materialize/HashJoinPlan.java`, `materialize/HashJoinScan.java` |
| 5B: Aggregates | `materialize/GroupByPlan.java`, `materialize/GroupByScan.java`, `materialize/AvgFn.java` |
| 6: DISTINCT | `plan/DistinctPlan.java`, `query/DistinctScan.java` |
| 6B: EXPLAIN | `plan/PlanPrinter.java`, `plan/Planner.java` |
| 7: Experiments | `opt/JoinMethods.java`, `experiment/JoinExperiment.java` |
| Bonus: Error checking | `plan/QueryVerifier.java` |
| Bonus: DOUBLE | `query/Constant.java`, `file/Page.java`, `record/Layout.java` |

**Bugs we fixed along the way:** RecordComparator didn't sort ascending fields; SortScan's save/restore skipped rows on duplicate merge-join keys; B-tree buffer leaks and too many open files on the large database.

---

## Known issues (not fixed in `main`)

1. **MergeJoinScan crash:** `beforeFirst()` doesn't reset `joinval`, so a restarted merge join (for example, under a nested-loop join) crashes. Repro: the `gradyear <= yearoffered order by title` query above.
2. **B-tree cost on tiny tables:** `BTreeIndex.searchCost` takes `log(0)` when the index has 0 blocks, so EXPLAIN shows about −2 billion blocks. Repro: `explain select eid, sectionid, grade from enroll where studentid = 4`. Fix: use at least 1 block in `searchCost`.
