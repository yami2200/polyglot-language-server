Polyglot.evalFile("python", "guest2.py")

Polyglot.export('var_value_int', 12)
Polyglot.export('var_value_str', "test")
Polyglot.export('var_value_arr', [12,257,23])
Polyglot.export('var_value_arr_mul', [12, "tser",257,23, "tsdfs"])
Polyglot.export('var_value_obj', {tet: 15, aass: "qf"})

let var_var_int = 1235
let var_var_str = "test"
let var_var_arr = [15,35,8,7]
let var_var_arr_mul = [12, "tser",257,23, "tsdfs"]
let var_var_obj = {age: 15, prenom: "qf"}

Polyglot.export('var_var_int', var_var_int)
Polyglot.export('var_var_str', var_var_str)
Polyglot.export('var_var_arr', var_var_arr)
Polyglot.export('var_var_arr_mul', var_var_arr_mul)
Polyglot.export('var_var_obj', var_var_obj)

// Recursive :

let var_value_int_rec_1 = Polyglot.import('var_value_int_rec_1')
let var_value_str_rec_1 = Polyglot.import('var_value_str_rec_1')
let var_value_arr_rec_1 = Polyglot.import('var_value_arr_rec_1')

Polyglot.export('var_value_int_rec', var_value_int_rec_1)
Polyglot.export('var_value_str_rec', var_value_str_rec_1)
Polyglot.export('var_value_arr_rec', var_value_arr_rec_1)

let var_var_int_rec_1 = Polyglot.import('var_var_int_rec_1')
let var_var_str_rec_1 = Polyglot.import('var_var_str_rec_1')
let var_var_arr_rec_1 = Polyglot.import('var_var_arr_rec_1')

Polyglot.export('var_var_int_rec', var_var_int_rec_1)
Polyglot.export('var_var_str_rec', var_var_str_rec_1)
Polyglot.export('var_var_arr_rec', var_var_arr_rec_1)