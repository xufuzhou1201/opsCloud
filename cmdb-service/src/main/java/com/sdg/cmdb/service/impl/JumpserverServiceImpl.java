package com.sdg.cmdb.service.impl;


import com.sdg.cmdb.dao.cmdb.KeyboxDao;
import com.sdg.cmdb.dao.cmdb.ServerDao;
import com.sdg.cmdb.dao.cmdb.ServerGroupDao;
import com.sdg.cmdb.dao.cmdb.UserDao;
import com.sdg.cmdb.dao.jumpserver.JumpserverDao;
import com.sdg.cmdb.domain.BusinessWrapper;
import com.sdg.cmdb.domain.auth.UserDO;
import com.sdg.cmdb.domain.jumpserver.*;
import com.sdg.cmdb.domain.server.ServerDO;
import com.sdg.cmdb.domain.server.ServerGroupDO;
import com.sdg.cmdb.service.CacheKeyService;
import com.sdg.cmdb.service.JumpserverService;
import com.sdg.cmdb.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
public class JumpserverServiceImpl implements JumpserverService {

    private static final Logger logger = LoggerFactory.getLogger(JumpserverServiceImpl.class);

    @Value("#{cmdb['jumpserver.host']}")
    private String jumpserverHost;

    //public final String adminuser_id = "ea301619659048d1ba262011f405b344";

    // TODO 用户组前缀
    public final String USERGROUP_PREFIX = "usergroup_";
    public final String SERVERGROUP_PREFIX = "group_";
    // TODO 授权规则前缀
    public final String PERMS_PREFIX = "perms_";

    // TODO 过期时间
    public final String DATA_EXPIRED = "2089-01-01 00:00:00";

    public final String CACHE_KEY = "JumpserverServiceImpl:";

    @Autowired
    private CacheKeyService cacheKeyService;


    @Autowired
    private JumpserverDao jumpserverDao;

    @Autowired
    private ServerGroupDao serverGroupDao;

    @Autowired
    private ServerDao serverDao;

    @Autowired
    private KeyboxDao keyboxDao;

    @Autowired
    private UserDao userDao;

    /**
     * TODO 生产随机主键
     *
     * @return
     */
    private String getId() {
        UUID uuid = UUID.randomUUID();
        return uuid.toString().replace("-", "");
    }

    private String getNowDate() {
        return TimeUtils.gmtNowDate();
    }

    private String getAdminuserId() {
        return cacheKeyService.getKeyByString(CACHE_KEY + "AdminuserId");
    }

    /**
     * 创建资产（主机）
     *
     * @param serverDO
     * @return
     */
    private AssetsAssetDO createAssetsAsset(ServerDO serverDO) {
        String adminuserId = getAdminuserId();
        if (StringUtils.isEmpty(adminuserId))
            return null;
        AssetsAssetDO assetsAssetDO;
        assetsAssetDO = jumpserverDao.getAssetsAssetByIp(serverDO.getInsideIp());
        if (assetsAssetDO != null) return assetsAssetDO;
        assetsAssetDO = new AssetsAssetDO(getId(), serverDO, adminuserId);
        jumpserverDao.addAssetsAsset(assetsAssetDO);
        return assetsAssetDO;
    }

    /**
     * 创建节点（服务器组）
     *
     * @param serverGroupDO
     * @return
     */
    private AssetsNodeDO createAssetsNode(ServerGroupDO serverGroupDO) {
        AssetsNodeDO assetsNodeDO = null;
        try {
            assetsNodeDO = jumpserverDao.getAssetsNodeByValue(serverGroupDO.getName());
            if (assetsNodeDO != null) return assetsNodeDO;
            int cnt = jumpserverDao.countAssetsNode();
            String key = "1:" + cnt + 1;
            assetsNodeDO = new AssetsNodeDO(getId(), serverGroupDO, key, getNowDate());
            jumpserverDao.addAssetsNode(assetsNodeDO);
            return assetsNodeDO;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return assetsNodeDO;
    }

    /**
     * 绑定资产到节点
     *
     * @param assetsAssetDO
     * @param assetsNodeDO
     * @return
     */
    private AssetsAssetNodesDO bindAssetsAssetNodes(AssetsAssetDO assetsAssetDO, AssetsNodeDO assetsNodeDO) {
        if (assetsAssetDO == null || assetsNodeDO == null) return null;
        AssetsAssetNodesDO assetsAssetNodesDO = new AssetsAssetNodesDO(assetsAssetDO.getId(), assetsNodeDO.getId());
        AssetsAssetNodesDO checkDO = jumpserverDao.getAssetsAssetNodes(assetsAssetNodesDO);
        if (checkDO == null) {
            try {
                jumpserverDao.addAssetsAssetNodes(assetsAssetNodesDO);
                return assetsAssetNodesDO;
            } catch (Exception e) {
                logger.error("Jumpserver 授权资产到节点 Error !");
                return null;
            }
        }
        return checkDO;
    }

    /**
     * 创建用户组
     *
     * @param serverGroupDO
     * @return
     */
    private UsersUsergroupDO createUsersUsergroup(ServerGroupDO serverGroupDO) {
        String name = serverGroupDO.getName().replace(SERVERGROUP_PREFIX, USERGROUP_PREFIX);
        UsersUsergroupDO usersUsergroupDO = jumpserverDao.getUsersUsergroupByName(name);
        if (usersUsergroupDO != null) return usersUsergroupDO;
        usersUsergroupDO = new UsersUsergroupDO(getId(), name, serverGroupDO.getContent(), getNowDate());
        try {
            jumpserverDao.addUsersUsergroup(usersUsergroupDO);
            return usersUsergroupDO;
        } catch (Exception e) {
            logger.error("Jumpserver 创建用户组 {} Error !", name);
            return null;
        }
    }

    /**
     * 创建授权策略
     *
     * @param serverGroupDO
     * @param assetsNodeDO
     * @param usersUsergroupDO
     * @return
     */
    private PermsAssetpermissionDO createPermsAssetpermission(ServerGroupDO serverGroupDO, AssetsNodeDO assetsNodeDO, UsersUsergroupDO usersUsergroupDO) {
        String name = serverGroupDO.getName().replace(SERVERGROUP_PREFIX, PERMS_PREFIX);

        PermsAssetpermissionDO permsAssetpermissionDO = jumpserverDao.getPermsAssetpermissionByName(name);
        if (permsAssetpermissionDO == null) {
            // TODO 新增授权策略
            permsAssetpermissionDO = new PermsAssetpermissionDO(getId(), name, DATA_EXPIRED, getNowDate());
            try {
                jumpserverDao.addPermsAssetpermission(permsAssetpermissionDO);
            } catch (Exception e) {
                return null;
            }
        }
        // TODO 绑定系统账户
        bindPermsAssetpermissionSystemUsers(permsAssetpermissionDO);
        // TODO 绑定用户组
        PermsAssetpermissionUserGroupsDO permsAssetpermissionUserGroupsDO = bindPermsAssetpermissionUserGroups(permsAssetpermissionDO, usersUsergroupDO);
        // TODO 绑定节点
        PermsAssetpermissionNodesDO bindPermsAssetpermissionNodes = bindPermsAssetpermissionNodes(permsAssetpermissionDO, assetsNodeDO);
        return permsAssetpermissionDO;
    }

    /**
     * 授权绑定用户组
     *
     * @param permsAssetpermissionDO
     * @param usersUsergroupDO
     * @return
     */
    private PermsAssetpermissionUserGroupsDO bindPermsAssetpermissionUserGroups(PermsAssetpermissionDO permsAssetpermissionDO, UsersUsergroupDO usersUsergroupDO) {
        PermsAssetpermissionUserGroupsDO permsAssetpermissionUserGroupsDO = new PermsAssetpermissionUserGroupsDO(permsAssetpermissionDO.getId(), usersUsergroupDO.getId());
        PermsAssetpermissionUserGroupsDO checkDO = jumpserverDao.getPermsAssetpermissionUserGroups(permsAssetpermissionUserGroupsDO);
        if (checkDO == null) {
            try {
                jumpserverDao.addPermsAssetpermissionUserGroups(permsAssetpermissionUserGroupsDO);
                return permsAssetpermissionUserGroupsDO;
            } catch (Exception e) {
                logger.error("Jumpserver 授权绑定用户组 Error !");
                return null;
            }
        }
        return checkDO;
    }

    /**
     * 授权绑定节点
     *
     * @param permsAssetpermissionDO
     * @param assetsNodeDO
     * @return
     */
    private PermsAssetpermissionNodesDO bindPermsAssetpermissionNodes(PermsAssetpermissionDO permsAssetpermissionDO, AssetsNodeDO assetsNodeDO) {
        PermsAssetpermissionNodesDO permsAssetpermissionNodesDO = new PermsAssetpermissionNodesDO(permsAssetpermissionDO.getId(), assetsNodeDO.getId());
        PermsAssetpermissionNodesDO checkDO = jumpserverDao.getPermsAssetpermissionNodes(permsAssetpermissionNodesDO);
        if (checkDO == null) {
            try {
                jumpserverDao.addPermsAssetpermissionNodes(permsAssetpermissionNodesDO);
                return permsAssetpermissionNodesDO;
            } catch (Exception e) {
                logger.error("Jumpserver 授权绑定节点 Error !");
                return null;
            }
        }
        return checkDO;
    }

    private String getSystemuserId() {
        return cacheKeyService.getKeyByString(CACHE_KEY + "SystemuserId");
    }

    /**
     * @param permsAssetpermissionDO
     * @return
     */
    private PermsAssetpermissionSystemUsersDO bindPermsAssetpermissionSystemUsers(PermsAssetpermissionDO permsAssetpermissionDO) {
        String systemuserId = getSystemuserId();
        if (StringUtils.isEmpty(systemuserId))
            return null;
        PermsAssetpermissionSystemUsersDO permsAssetpermissionSystemUsersDO = new PermsAssetpermissionSystemUsersDO(permsAssetpermissionDO.getId(), systemuserId);
        PermsAssetpermissionSystemUsersDO checkDO = jumpserverDao.getPermsAssetpermissionSystemUsers(permsAssetpermissionSystemUsersDO);
        if (checkDO == null) {
            try {
                jumpserverDao.addPermsAssetpermissionSystemUsers(permsAssetpermissionSystemUsersDO);
                return permsAssetpermissionSystemUsersDO;
            } catch (Exception e) {
                logger.error("Jumpserver 授权绑定系统账户 Error !");
                return null;
            }
        }
        return checkDO;


    }

    /**
     * 同步资产 包括（主机，服务器组，用户组，授权等）
     *
     * @return
     */
    @Override
    public BusinessWrapper<Boolean> syncAssets() {
        List<ServerGroupDO> groupList = serverGroupDao.queryServerGroup();
        for (ServerGroupDO serverGroupDO : groupList) {
            // TODO 创建资产节点（服务器组）
            AssetsNodeDO assetsNodeDO = createAssetsNode(serverGroupDO);
            // TODO 创建用户组
            UsersUsergroupDO usersUsergroupDO = createUsersUsergroup(serverGroupDO);
            if (assetsNodeDO == null) {
                logger.error("Jumpserver 同步资产树（服务器组）{} Error !", serverGroupDO.getName());
                continue;
            }
            // TODO 创建授权并绑定 节点，用户组，系统账户
            PermsAssetpermissionDO permsAssetpermissionDO = createPermsAssetpermission(serverGroupDO, assetsNodeDO, usersUsergroupDO);
            // TODO 同步资产并绑定 节点
            List<ServerDO> serverList = serverDao.acqServersByGroupId(serverGroupDO.getId());
            for (ServerDO serverDO : serverList) {
                try {
                    // TODO 创建资产（主机）
                    AssetsAssetDO assetsAssetDO = createAssetsAsset(serverDO);
                    // TODO 绑定资产到节点
                    bindAssetsAssetNodes(assetsAssetDO, assetsNodeDO);
                } catch (Exception e) {
                    // e.printStackTrace();
                }
            }
        }
        return new BusinessWrapper<Boolean>(true);
    }


    /**
     * 同步用户（包括授权信息）
     */
    @Override
    public BusinessWrapper<Boolean> syncUsers() {
        List<UserDO> userList = userDao.getAllUser();
        for (UserDO userDO : userList) {
            bindUserGroups(userDO);
        }
        return new BusinessWrapper<Boolean>(true);
    }

    /**
     * 创建Jumpserver用户
     *
     * @param userDO
     * @return
     */
    private UsersUserDO createUsersUser(UserDO userDO) {
        UsersUserDO usersUserDO = jumpserverDao.getUsersUserByUsername(userDO.getUsername());
        if (usersUserDO == null) {
            //  public UsersUserDO(UserDO userDO, String id, String date_joined, String date_expired)
            usersUserDO = new UsersUserDO(userDO, getId(), getNowDate(), DATA_EXPIRED);
            try {
                jumpserverDao.addUsersUser(usersUserDO);
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("Jumpserver 创建用户{} Error !", userDO.getUsername());
                return null;
            }
        }
        return usersUserDO;
    }

    /**
     * 绑定用户-用户组
     *
     * @param userDO
     * @return
     */
    private void bindUserGroups(UserDO userDO) {
        // TODO 查询用户的所有授权的服务组
        List<ServerGroupDO> serverGroupList = keyboxDao.getGroupListByUsername(userDO.getUsername());
        // TODO 创建用户
        UsersUserDO usersUserDO = createUsersUser(userDO);
        for (ServerGroupDO serverGroupDO : serverGroupList) {
            // TODO 用户组名称
            String name = serverGroupDO.getName().replace(SERVERGROUP_PREFIX, USERGROUP_PREFIX);
            UsersUsergroupDO usersUsergroupDO = jumpserverDao.getUsersUsergroupByName(name);
            if (usersUsergroupDO == null)
                usersUsergroupDO = createUsersUsergroup(serverGroupDO);
            bindUserGroups(usersUserDO, usersUsergroupDO);
        }
    }

    private boolean bindUserGroups(UsersUserDO usersUserDO, UsersUsergroupDO usersUsergroupDO) {
        if (usersUserDO == null || usersUsergroupDO == null) return false;
        UsersUserGroupsDO usersUserGroupsDO = new UsersUserGroupsDO(usersUserDO.getId(), usersUsergroupDO.getId());
        UsersUserGroupsDO checkDO = jumpserverDao.getUsersUserGroups(usersUserGroupsDO);
        if (checkDO == null) {
            try {
                jumpserverDao.addUsersUserGroups(usersUserGroupsDO);
            } catch (Exception e) {
                logger.error("Jumpserver 用户绑定用户组 Error !");
                return false;
            }
        }
        return true;
    }

    @Override
    public List<AssetsSystemuserDO> queryAssetsSystemuser(String name) {
        if (StringUtils.isEmpty(name))
            name = "";
        List<AssetsSystemuserDO> list = jumpserverDao.queryAssetsSystemuser(name);
        return list;
    }

    @Override
    public List<AssetsAdminuserDO> queryAssetsAdminuser(String name) {
        if (StringUtils.isEmpty(name))
            name = "";
        List<AssetsAdminuserDO> list = jumpserverDao.queryAssetsAdminuser(name);
        return list;
    }

    @Override
    public JumpserverVO getJumpserver() {
        JumpserverVO jumpserverVO = new JumpserverVO();

        if (StringUtils.isEmpty(jumpserverHost))
            return jumpserverVO;
        jumpserverVO.setHost(jumpserverHost);
        String systemuserId = cacheKeyService.getKeyByString(this.CACHE_KEY + "SystemuserId");
        // TODO 插入配置的当前系统账户
        if (!StringUtils.isEmpty(systemuserId)) {
            AssetsSystemuserDO assetsSystemuserDO = jumpserverDao.getAssetsSystemuser(systemuserId);
            jumpserverVO.setAssetsSystemuserDO(assetsSystemuserDO);
        }

        String adminuserId = cacheKeyService.getKeyByString(this.CACHE_KEY + "AdminuserId");
        // TODO 插入配置的当前系统账户
        if (!StringUtils.isEmpty(adminuserId)) {
            AssetsAdminuserDO assetsAdminuserDO = jumpserverDao.getAssetsAdminuser(adminuserId);
            jumpserverVO.setAssetsAdminuserDO(assetsAdminuserDO);
        }

        return jumpserverVO;
    }

    @Override
    public BusinessWrapper<Boolean> saveAssetsSystemuser(String id) {
        if (StringUtils.isEmpty(id))
            return new BusinessWrapper<Boolean>(true);
        try {
            cacheKeyService.set(CACHE_KEY + "SystemuserId", id);
            return new BusinessWrapper<Boolean>(true);
        } catch (Exception e) {
            return new BusinessWrapper<Boolean>(false);
        }
    }

    @Override
    public BusinessWrapper<Boolean> saveAssetsAdminuser(String id) {
        if (StringUtils.isEmpty(id))
            return new BusinessWrapper<Boolean>(true);
        try {
            cacheKeyService.set(CACHE_KEY + "AdminuserId", id);
            return new BusinessWrapper<Boolean>(true);
        } catch (Exception e) {
            return new BusinessWrapper<Boolean>(false);
        }
    }

}
