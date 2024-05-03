import json

# Asegúrate de especificar la ruta correcta al archivo de credenciales
with open('P:\\PPP\\PQSolutions\\APP\KEYS APP\\fir-hdd-monitor-firebase-adminsdk-l8ui2-f517870b2b.json', 'r') as file:
    credentials_dict = json.load(file)

credentials_string = json.dumps(credentials_dict)
print(credentials_string)