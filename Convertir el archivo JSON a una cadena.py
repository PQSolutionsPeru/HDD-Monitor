import json

# Carga tu archivo JSON
with open('P:\\PPP\\PQSolutions\\APP\\KEYS APP\\fir-hdd-monitor-firebase-adminsdk-l8ui2-f517870b2b.json', 'r') as file:
    credentials_dict = json.load(file)

# Convierte el diccionario a una cadena JSON
credentials_string = json.dumps(credentials_dict)
print(credentials_string)
