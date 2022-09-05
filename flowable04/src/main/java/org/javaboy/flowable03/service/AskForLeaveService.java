package org.javaboy.flowable03.service;

import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.javaboy.flowable03.model.ApproveRejectVO;
import org.javaboy.flowable03.model.AskForLeaveVO;
import org.javaboy.flowable03.model.HistoryInfo;
import org.javaboy.flowable03.model.RespBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * @author 江南一点雨
 * @微信公众号 江南一点雨
 * @网站 http://www.itboyhub.com
 * @国际站 http://www.javaboy.org
 * @微信 a_java_boy
 * @GitHub https://github.com/lenve
 * @Gitee https://gitee.com/lenve
 */
@Service
public class AskForLeaveService {

    @Autowired
    RuntimeService runtimeService;

    @Autowired
    TaskService taskService;

    @Autowired
    HistoryService historyService;

    @Transactional
    public RespBean askForLeave(AskForLeaveVO askForLeaveVO) {
        Map<String, Object> variables = new HashMap<>();
        askForLeaveVO.setName(SecurityContextHolder.getContext().getAuthentication().getName());
        variables.put("name", askForLeaveVO.getName());
        variables.put("days", askForLeaveVO.getDays());
        variables.put("reason", askForLeaveVO.getReason());
        System.out.println("askForLeaveVO.getApproveUsers() = " + askForLeaveVO.getApproveUsers());
        variables.put("userTasks", askForLeaveVO.getApproveUsers());
        try {
            runtimeService.startProcessInstanceByKey("holidayRequest", askForLeaveVO.getName(), variables);
            return RespBean.ok("已提交请假申请");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return RespBean.error("提交申请失败");
    }

    /**
     * 待审批列表
     *
     * @return
     */
    public RespBean leaveList() {
        String identity = SecurityContextHolder.getContext().getAuthentication().getName();
        //找到所有分配给你的任务
        List<Task> tasks = taskService.createTaskQuery().taskAssignee(identity).list();
        //重新组装返回的数据，为每个流程增加任务 id，方便后续执行批准或者拒绝操作
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            Map<String, Object> variables = taskService.getVariables(task.getId());
            variables.put("id", task.getId());
            list.add(variables);
        }
        return RespBean.ok("加载成功", list);
    }

    public RespBean askForLeaveHandler(ApproveRejectVO approveRejectVO) {
        try {
            Task task = taskService.createTaskQuery().taskId(approveRejectVO.getTaskId()).singleResult();
            boolean approved = approveRejectVO.getApprove();
            Map<String, Object> variables = new HashMap<String, Object>();
            variables.put("approved", approved);
            variables.put("approveUser#" + task.getAssignee(), SecurityContextHolder.getContext().getAuthentication().getName());
            taskService.complete(task.getId(), variables);
            return RespBean.ok("操作成功");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return RespBean.error("操作失败");
    }

    public RespBean searchResult() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        List<HistoryInfo> infos = new ArrayList<>();
        //未完成流程
        List<HistoricProcessInstance> unFinishedHistoricProcessInstances = historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(name).unfinished().orderByProcessInstanceStartTime().desc().list();
        for (HistoricProcessInstance unFinishedHistoricProcessInstance : unFinishedHistoricProcessInstances) {
            HistoryInfo historyInfo = new HistoryInfo();
            Date startTime = unFinishedHistoricProcessInstance.getStartTime();
            Date endTime = unFinishedHistoricProcessInstance.getEndTime();
            List<HistoricVariableInstance> historicVariableInstances = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(unFinishedHistoricProcessInstance.getId())
                    .list();
            System.out.println("historicVariableInstances = " + historicVariableInstances);
            for (HistoricVariableInstance historicVariableInstance : historicVariableInstances) {
                String variableName = historicVariableInstance.getVariableName();
                Object value = historicVariableInstance.getValue();
                if ("reason".equals(variableName)) {
                    historyInfo.setReason((String) value);
                } else if ("days".equals(variableName)) {
                    historyInfo.setDays(Integer.parseInt(value.toString()));
                } else if ("name".equals(variableName)) {
                    historyInfo.setName((String) value);
                } else if (variableName.startsWith("approveUser")) {
                    historyInfo.getApproveUsers().add((String) value);
                } else if ("userTask".equals(variableName)) {
                    historyInfo.getCandidateUsers().add((String) value);
                }
            }
            historyInfo.setStatus(3);
            historyInfo.setStartTime(startTime);
            historyInfo.setEndTime(endTime);
            infos.add(historyInfo);
        }

        //已结束流程
        List<HistoricProcessInstance> finishHistoricProcessInstances = historyService.createHistoricProcessInstanceQuery().processInstanceBusinessKey(name)
                .finished()
                .orderByProcessInstanceStartTime().desc().list();
        for (HistoricProcessInstance historicProcessInstance : finishHistoricProcessInstances) {
            HistoryInfo historyInfo = new HistoryInfo();
            Date startTime = historicProcessInstance.getStartTime();
            Date endTime = historicProcessInstance.getEndTime();
            List<HistoricVariableInstance> historicVariableInstances = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(historicProcessInstance.getId())
                    .list();
            System.out.println(historicVariableInstances);
            for (HistoricVariableInstance historicVariableInstance : historicVariableInstances) {
                String variableName = historicVariableInstance.getVariableName();
                Object value = historicVariableInstance.getValue();
                if ("reason".equals(variableName)) {
                    historyInfo.setReason((String) value);
                } else if ("days".equals(variableName)) {
                    historyInfo.setDays(Integer.parseInt(value.toString()));
                } else if ("approved".equals(variableName)) {
                    Boolean v = (Boolean) value;
                    if (v) {
                        historyInfo.setStatus(1);
                    } else {
                        historyInfo.setStatus(2);
                    }
                } else if ("name".equals(variableName)) {
                    historyInfo.setName((String) value);
                } else if (variableName.startsWith("approveUser")) {
                    historyInfo.getApproveUsers().add((String) value);
                } else if ("userTask".equals(variableName)) {
                    historyInfo.getCandidateUsers().add((String) value);
                }
            }
            historyInfo.setStartTime(startTime);
            historyInfo.setEndTime(endTime);
            infos.add(historyInfo);
        }
        return RespBean.ok("ok", infos);
    }
}