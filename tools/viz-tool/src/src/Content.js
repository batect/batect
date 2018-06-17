import React from "react";
import {Route} from "react-router-dom";
import Typography from "@material-ui/core/Typography";
import Summary from "./Summary";
import TaskGraph from "./TaskGraph";
import ContainerGraph from "./ContainerGraph";
import Timeline from "./Timeline";

const Content = () => {
    return (
        <Typography component="main" style={{ padding: 8 * 3 }}>
            <Route path="/" exact component={Summary}/>
            <Route path="/tasks" exact component={TaskGraph}/>
            <Route path="/containers" exact component={ContainerGraph}/>
            <Route path="/timeline" exact component={Timeline}/>
        </Typography>
    );
};

export default Content;
