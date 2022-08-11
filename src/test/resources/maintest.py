import polyglot
print("test file in python")

polyglot.eval(language='javascript', path='guestjstest.js')

var_var_arr = polyglot.import_value('var_var_arr')