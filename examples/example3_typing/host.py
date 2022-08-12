import polyglot
polyglot.eval(language='javascript', path='guest1.js')

# FROM JS

var_value_int = polyglot.import_value('var_value_int')
var_value_str = polyglot.import_value('var_value_str')
var_value_arr = polyglot.import_value('var_value_arr')
var_value_arr_mul = polyglot.import_value('var_value_arr_mul')
var_value_obj = polyglot.import_value('var_value_obj')

var_var_int = polyglot.import_value('var_var_int')
var_var_str = polyglot.import_value('var_var_str')
var_var_arr = polyglot.import_value('var_var_arr')
var_var_arr_mul = polyglot.import_value('var_var_arr_mul')
var_var_obj = polyglot.import_value('var_var_obj')

# FROM PYTHON

var_value_int_py = polyglot.import_value('var_value_int_py')
var_value_str_py = polyglot.import_value('var_value_str_py')
var_value_arr_py = polyglot.import_value('var_value_arr_py')

var_var_int_py = polyglot.import_value('var_var_int_py')
var_var_str_py = polyglot.import_value('var_var_str_py')
var_var_arr_py = polyglot.import_value('var_var_arr_py')

# FROM PYTHON RECURSIVE

var_value_int_rec = polyglot.import_value('var_value_int_rec')
var_value_str_rec = polyglot.import_value('var_value_str_rec')
var_value_arr_rec = polyglot.import_value('var_value_arr_rec')

var_var_int_rec = polyglot.import_value('var_var_int_rec')
var_var_str_rec = polyglot.import_value('var_var_str_rec')
var_var_arr_rec = polyglot.import_value('var_var_arr_rec')

print(var_var_obj.aass)
print(var_var_arr_mul)