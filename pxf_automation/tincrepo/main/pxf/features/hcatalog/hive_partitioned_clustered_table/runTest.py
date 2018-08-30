from mpp.models import SQLTestCase
from mpp.models import SQLConcurrencyTestCase

class HcatalogPartitionedClusteredTable(SQLConcurrencyTestCase):
    """
    @product_version  hawq: [2.0-]
    @db_name pxfautomation
    @concurrency 1
    @gpdiff True
    """
    sql_dir = 'sql'
    ans_dir = 'expected'
    out_dir = 'output'