#!/usr/bin/python

import sqlite3
import sys

import dataextract
import stupidplot

def xaxis(cursor, xaxis_column, yaxis_column, where = None, **filters):
    """Plot the xaxis_column on the x-axis. Plot "requests per second" on the
    y-axis. Filter using filters. Group by the remaining columns, drawing each one as its own
    line."""

    # Get all the columns
    cursor.execute("select * from results limit 0")
    column_list = [descriptor[0] for descriptor in cursor.description]

    xaxis_index = column_list.index(xaxis_column)
    yaxis_index = column_list.index(yaxis_column)
    
    remaining_columns = list(column_list)
    remaining_columns.remove(xaxis_column)
    remaining_columns.remove(yaxis_column)

    statement = "select * from results"

    # Build the where clause
    if where is not None:
        statement += " where (" + where + ")"
    for i, (name, value) in enumerate(filters.iteritems()):
        remaining_columns.remove(name)

        if i == 0 and where is None:
            statement += " where "
        else:
            statement += " and "
        statement += name + " = "
        if isinstance(value, basestring):
            statement += "'%s'" % value
        else:
            statement += str(value)

    statement += " order by " + ",".join(remaining_columns + [xaxis_column])

    results = cursor.execute(statement)

    output = []
    current_results = None
    last_type = None
    for r in results:
        # Build the "type" for the row
        row_type = []
        for column in remaining_columns:
            index = column_list.index(column)
            row_type.append(r[index])

        # New type? Make a new output table
        if row_type != last_type:
            if current_results is not None:
                output.append(current_results)
            current_results = [[xaxis_column, str(row_type)]]
            last_type = row_type
        current_results.append((r[xaxis_index], r[yaxis_index]))

    if current_results is not None:
        output.append(current_results)
    
    return output


if __name__ == "__main__":
    input_path = sys.argv[1]

    # Load the CSV data
    table = dataextract.readCSVTable(input_path)

    # Load the data into SQLite
    connection = sqlite3.connect(":memory:")
    dataextract.sqlFromTable(connection, "results", table)
    c = connection.cursor()

    charsets = []
    for r in connection.execute("select distinct charset from results"):
        charsets.append(r[0])

    for charset in charsets:
        output = xaxis(c, "string_length", "chars_per_us", charset=charset)
        average_out = []
        for table in output:
            average_table = [table[0]]
            last_x = table[1][0]
            sum = 0
            count = 0
            for row in table[1:]:
                if last_x != row[0]:
                    average_table.append((last_x, sum/count))
                    sum = 0
                    count = 0
                last_x = row[0]
                sum += row[1]
                count += 1
            average_table.append((last_x, sum/count))
            average_out.append(average_table)

        options = {
            #~ "plottype": "points",
            "ylabel": "UTF-16 chars per us",
            "key": "bottom right",
        }
        stupidplot.gnuplotTable(average_out, "stringbytebuffer-%s.eps" % charset, options)

