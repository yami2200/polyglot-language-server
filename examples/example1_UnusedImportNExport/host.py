import polyglot
polyglot.import_value('NotExistedVariable')
polyglot.eval(language='js', path='guest.js')

# Evaluation not found
polyglot.eval(language='js', path='notfoundfile.js')
