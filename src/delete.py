import todoList


def delete(event, context):
    todoList.delete_item(event['pathParameters']['id'])

    # create a response
    response = {
    "statusCode": 200,
    "headers": {
        "Content-Type": "application/json"
    },
    "body": json.dumps({"message": "Item deleted"})
    }


    return response
