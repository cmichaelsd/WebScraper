from pydantic import AnyUrl


class HttpOnlyUrl(AnyUrl):
    allowed_schemes = {"http", "https"}