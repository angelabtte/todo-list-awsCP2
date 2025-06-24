import json
import decimalencoder
import todoList


def get(event, context):
    # create a response
    item = todoList.get_item(event['pathParameters']['id'])
    if item:
    response = {
        "statusCode": 200,
        "headers": {
            "Content-Type": "application/json"
        },
        "body": json.dumps(item, cls=decimalencoder.DecimalEncoder)
    }
else:
    response = {
        "statusCode": 404,
        "headers": {
            "Content-Type": "application/json"
        },
        "body": ""
    }
    return response
