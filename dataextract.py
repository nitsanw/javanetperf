#!/usr/bin/python

"""Data Extraction Tools

Ideally this module will be a set of reusable tools for extracting and summarizing data stored in
CSV files. Right now it is very crappy and unorganized."""

import csv
import sqlite3

import stupidplot

def tryFloat(s):
    try:
        return float(s)
    except ValueError:
        return s

def readCSVTable(path):
    """Returns a 2D list from the csv file in path."""

    data = open(path)
    reader = csv.reader(data)
    table = []
    for row in reader:
        table.append([tryFloat(s) for s in row])
    data.close()

    return table

def writeCSVTable(table, output_path):
    out = open(output_path, "wb")
    writer = csv.writer(out)
    writer.writerows(table)
    out.close()

def expandRows(table, key_columns, value_columns):
    """Expands a row with multiple data points into one row per data point."""

    out = []
    for in_row in table:
        # Pull the key columns from in_row into a template
        out_row_template = []
        for key_column in key_columns:
            out_row_template.append(in_row[key_column])

        # For each value, output a row with (key, value)
        for value_column in value_columns:
            out_row = list(out_row_template)
            out_row.append(in_row[value_column])
            out.append(out_row)

    return out

def extractTables(path, x_axis_title, y_axis_title, line_title):
    """Returns two tables containing the values from the CSV file in path. The first contains the
    the columns (x, average, -95% confidence, +95% confidence). The second contains (x, y)
    scatter plot values."""

    table = readCSVTable(path)

    scatter = [(x_axis_title, y_axis_title)]
    average = [(x_axis_title, line_title, "-95% confidence", "+95% confidence")]
    for row in table:
        x = float(row[0])
        for y in row[1:]:
            scatter.append((x, y))
        stats = stupidplot.stats([float(f) for f in row[1:]])
        average.append([x, stats[0], stats[0]-stats[-1], stats[0]+stats[-1]])

    return average, scatter

def insertColumn(table, index, value):
    out_table = []
    for row in table:
        r = list(row)
        r.insert(index, value)
        out_table.append(r)
    return out_table


def groupBy(table, group_columns):
    groups = {}
    for row in table:
        key = []
        for column in group_columns:
            key.append(row[column])
        key = tuple(key)

        if key not in groups:
            groups[key] = []
        groups[key].append(row)
    return groups


def project(table, columns):
    out_table = []
    for row in table:
        r = []
        for column in columns:
            r.append(row[column])
        out_table.append(r)
    return out_table


def select(table, equalityConditions):
    out_table = []
    for row in table:
        match = True
        for column, value in equalityConditions:
            if row[column] != value:
                match = False
                break
        if match:
            out_table.append(row)
    return out_table


def unique(table, column):
    """Returns a list of unique values in column."""

    keys = groupBy(table, [column]).keys()
    keys.sort()
    return keys


def selectStats(table, group_columns, value_column):
    """Stats are (average, median, standard deviation, min, max, 95% confidence interval)"""

    groups = groupBy(table, group_columns)

    out = []
    keys = groups.keys()
    keys.sort()
    for key in keys:
        values = []
        for row in groups[key]:
            values.append(row[value_column])

        stats = stupidplot.stats(values)
        out.append(key + stats)

    return out


def selectStatsConfPlot(table, group_columns, value_column):
    """Stats are (average, median, standard deviation, min, max, 95% confidence interval)"""

    table = selectStats(table, group_columns, value_column)
    
    out_table = []
    for row in table:
        r = list(row[:-6])
        r.append(row[-6])
        r.append(row[-6] - row[-1])
        r.append(row[-6] + row[-1])
        out_table.append(r)
    return out_table


def sqlFromTable(connection, table_name, table):
    """Load data in table into a SQL table with name table_name using connection."""

    c = connection.cursor()

    # Create the table from the CSV headers
    header = list(table[0])
    for i, column_name in enumerate(header):
        header[i] = column_name.replace(" ", "_")
    c.execute("create table %s (%s);" % (table_name, ", ".join(header)))

    # Import the data
    insert_template = "?," * len(table[0])
    insert_template = "insert into results values (%s)" % (insert_template[:-1])
    for row in table[1:]:
        c.execute(insert_template, row)
    c.close()


def foo(path):
    # Read in the table
    table = readCSVTable(path)

    # Expand each repeated data point
    table = expandRows(table, [0, 1], [2, 3, 4, 5, 6])
    table.insert(0, ["conflict_percent", "multipartition_percent", "txns_per_sec"])

    connection = sqlite3.connect(":memory:")
    sqlFromTable(connection, "results", table)

    c = connection.cursor()
    datasets = []
    for v in list(c.execute("select distinct conflict_percent from results")):
        conflict_percent = v[0]
        #~ legends.append("Lock %.0f%% conflict" % (conflict_percent))
        table = [["% Multipartition", "Lock %.0f%% conflict" % (conflict_percent), "- conf", "+ conf"]]
        for y in list(c.execute("select distinct multipartition_percent from results where "
                "conflict_percent = ? order by multipartition_percent asc", [conflict_percent])):
            y = y[0]
            x_values = []
            for x in c.execute("select txns_per_sec from results where conflict_percent = ? and multipartition_percent = ?", (conflict_percent, y)):
                x_values.append(x[0])

            stats = stupidplot.stats(x_values)
            table.append([y, stats[0], stats[0]-stats[-1], stats[0]+stats[-1]])
            print table[-1]
        datasets.append(table)
    c.close()

    return datasets
