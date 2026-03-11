import boto3

class S3Repository:
    def __init__(self, bucket_name: str):
        self.s3 = boto3.client('s3')
        self.bucket = bucket_name

    def upload_image(self, s3_key: str, image_bytes: bytes) -> str:
        self.s3.put_object(
            Bucket=self.bucket,
            Key=s3_key,
            Body=image_bytes,
            ContentType="image/jpeg",
            ServerSideEncryption="AES256"
        )
        return f"s3://{self.bucket}/{s3_key}"

    def get_presigned_url(self, s3_key: str, expires_in: int = 3600) -> str:
        return self.s3.generate_presigned_url(
            'get_object',
            Params={'Bucket': self.bucket, 'Key': s3_key},
            ExpiresIn=expires_in
        )
