import json
import csv

csvInput = '2019-Nov.csv'
jsonOutput = "data-Oct.json"
i = 0
print("Initializing conversion from .CSV to .JSON, this may take some time")
with open(csvInput, 'r') as input_file:
    reader = csv.DictReader(input_file)
    with open(jsonOutput, 'a') as output_file:
        for row in reader:
            if(i==0):
                output_file.write("[\n")
                json.dump(row, output_file)
                output_file.write("\n")
                i = 1
            else:
                output_file.write(',')
                json.dump(row,output_file)
                output_file.write("\n")
        output_file.write("]")