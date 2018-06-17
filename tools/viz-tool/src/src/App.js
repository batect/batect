import React from 'react';
import PropTypes from 'prop-types';
import {createMuiTheme, MuiThemeProvider, withStyles} from '@material-ui/core/styles';
import CssBaseline from '@material-ui/core/CssBaseline';
import {BrowserRouter as Router} from "react-router-dom";
import TabBar from './TabBar';
import Content from './Content';

const styles = () => ({
    root: {
        flexGrow: 1,
    }
});

const theme = createMuiTheme({
    palette: {
        background: {
            default: '#fff'
        }
    }
});

const App = ({classes}) => {
    return (
        <Router>
            <MuiThemeProvider theme={theme}>
                <div className={classes.root}>
                    <CssBaseline/>
                    <TabBar/>
                    <Content/>
                </div>
            </MuiThemeProvider>
        </Router>
    );
};


App.propTypes = {
    classes: PropTypes.object.isRequired,
};

export default withStyles(styles)(App);
