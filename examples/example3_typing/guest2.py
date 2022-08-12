import polyglot

polyglot.export_value(value=12, name='var_value_int_py')
polyglot.export_value(value="test", name='var_value_str_py')
polyglot.export_value(value=[12,257,23], name='var_value_arr_py')

var_var_int_py = 1235
var_var_str_py = "test"
var_var_arr_py = [15,35,8,7]

polyglot.export_value(value=var_var_int_py, name='var_var_int_py')
polyglot.export_value(value=var_var_str_py, name='var_var_str_py')
polyglot.export_value(value=var_var_arr_py, name='var_var_arr_py')

# Source Recursive :

polyglot.export_value(value=12, name='var_value_int_rec_1')
polyglot.export_value(value="test", name='var_value_str_rec_1')
polyglot.export_value(value=[12,257,23], name='var_value_arr_rec_1')

polyglot.export_value(value=var_var_int_py, name='var_var_int_rec_1')
polyglot.export_value(value=var_var_str_py, name='var_var_str_rec_1')
polyglot.export_value(value=var_var_arr_py, name='var_var_arr_rec_1')