package com.sdg.cmdb.domain.zabbix.response;

import com.alibaba.fastjson.JSON;
import lombok.Data;


@Data
public class ZabbixResponseTemplate extends ZabbixResponseResult {

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
